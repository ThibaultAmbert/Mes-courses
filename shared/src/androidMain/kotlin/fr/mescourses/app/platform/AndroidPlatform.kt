package fr.mescourses.app.platform

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.Uri
import android.webkit.CookieManager
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import fr.mescourses.app.data.AppDatabase
import fr.mescourses.app.data.AppUpdater
import fr.mescourses.app.data.CatalogDatabase
import fr.mescourses.app.data.isOnline
import fr.mescourses.app.data.settingsDataStore
import fr.mescourses.app.llm.AndroidFileDownloader
import fr.mescourses.app.llm.LiteRtLmEngine
import fr.mescourses.app.llm.LiteRtTextEmbedder
import fr.mescourses.app.llm.LlmEngine
import fr.mescourses.app.llm.copyUriToDir
import fr.mescourses.app.recipes.WebViewPageFetcher
import fr.mescourses.app.retail.RetailerBridge
import fr.mescourses.app.retail.RetailerSite
import fr.mescourses.app.retail.RetailerWebViewBridge
import fr.mescourses.app.work.AndroidBackgroundWork
import fr.mescourses.core.recipes.PageFetcher
import fr.mescourses.core.retail.Retailer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Android : les implémentations d'origine de l'app, branchées derrière [Platform]. */
class AndroidPlatform(context: Context) : Platform {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val kind = PlatformKind.ANDROID
    override val filesDir: String get() = app.filesDir.absolutePath
    override val cacheDir: String get() = app.cacheDir.absolutePath

    override fun appDatabase(): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder(app, AppDatabase::class.java, "mes-courses.db")

    override fun catalogDatabase(): RoomDatabase.Builder<CatalogDatabase> =
        Room.databaseBuilder(app, CatalogDatabase::class.java, "catalogue.db")

    override val settingsDataStore: DataStore<Preferences> get() = app.settingsDataStore

    override fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(CIO, config)

    override fun webPageFetcher(): PageFetcher = WebViewPageFetcher(app)

    override fun llmEngine(): LlmEngine = LiteRtLmEngine(app)

    override fun textEmbedder(modelPath: String): AppTextEmbedder = LiteRtTextEmbedder(app, File(modelPath))

    override val downloader: FileDownloader = AndroidFileDownloader

    override fun retailerBridge(
        site: RetailerSite,
        uaWithoutWv: () -> Boolean,
        leclercUaMode: () -> String,
        onRequestSeen: ((url: String, headers: Map<String, String>) -> Unit)?,
    ): RetailerBridge = RetailerWebViewBridge(app, site, uaWithoutWv, leclercUaMode, onRequestSeen)

    override val cookies: CookieJar = object : CookieJar {
        override fun has(url: String, name: String): Boolean =
            CookieManager.getInstance().getCookie(url)?.contains("$name=") == true

        override fun flush() = CookieManager.getInstance().flush()

        override fun clearAll() {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    override fun isOnline(): Boolean = isOnline(app)

    override fun isMetered(): Boolean =
        app.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    override val background: BackgroundWork = AndroidBackgroundWork(app, scope)

    override fun appUpdater(scope: CoroutineScope, http: HttpClient): AppUpdates = AppUpdater(app, scope, http)

    override suspend fun copyPickedFile(file: PickedFile, targetDir: String): String =
        copyUriToDir(app, file.handle as Uri, targetDir)

    override suspend fun geocode(postalCode: String): GeoPlace? = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        runCatching { Geocoder(app, Locale.FRANCE).getFromLocationName("$postalCode, France", 1)?.firstOrNull() }.getOrNull()
            ?.let { GeoPlace(it.latitude, it.longitude, it.locality ?: "") }
    }

    private fun launchIntent(retailer: Retailer): Intent? =
        runCatching { app.packageManager.getLaunchIntentForPackage(retailer.appPackage) }.getOrNull()

    override fun retailerAppAvailable(retailer: Retailer): Boolean = launchIntent(retailer) != null

    override fun openRetailerApp(retailer: Retailer) {
        launchIntent(retailer)?.let { runCatching { app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    override fun defaultWebUserAgent(): String =
        runCatching { android.webkit.WebSettings.getDefaultUserAgent(app) }.getOrDefault("Mozilla/5.0 (Linux; Android 14)")

    override fun shareFile(path: String, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(app, app.packageName + ".fichiers", File(path))
        app.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                title,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
