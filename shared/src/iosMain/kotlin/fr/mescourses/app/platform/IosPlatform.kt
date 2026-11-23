package fr.mescourses.app.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import fr.mescourses.app.data.AppDatabase
import fr.mescourses.app.data.CatalogDatabase
import fr.mescourses.app.llm.ChatEvent
import fr.mescourses.app.llm.IosLlmEngine
import fr.mescourses.app.llm.IosTextEmbedder
import fr.mescourses.app.llm.LlmEngine
import fr.mescourses.app.llm.NativeEmbedder
import fr.mescourses.app.llm.NativeLlm
import fr.mescourses.app.recipes.WkPageFetcher
import fr.mescourses.app.retail.RetailerBridge
import fr.mescourses.app.retail.RetailerSite
import fr.mescourses.app.retail.WkRetailerBridge
import fr.mescourses.core.agent.AgentTool
import fr.mescourses.core.llm.Msg
import fr.mescourses.core.recipes.PageFetcher
import fr.mescourses.core.retail.Retailer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLPlacemark
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UIKit.UIViewController
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * iOS : implémentations natives derrière [Platform]. Le LLM et l'embedder sont fournis par le code Swift
 * de l'app (LiteRT-LM) ; sans eux, l'app marche sans assistant (comme Android sans modèle installé).
 */
class IosPlatform(
    private val nativeLlm: NativeLlm?,
    private val nativeEmbedder: NativeEmbedder?,
) : Platform {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val kind = PlatformKind.IOS

    override val filesDir: String = directory(NSApplicationSupportDirectory, excludeFromBackup = true)
    override val cacheDir: String = directory(NSCachesDirectory, excludeFromBackup = false)

    private fun directory(kind: ULong, excludeFromBackup: Boolean): String {
        val base = NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as String
        NSFileManager.defaultManager.createDirectoryAtPath(base, withIntermediateDirectories = true, attributes = null, error = null)
        // Modèles de plusieurs Go : jamais dans la sauvegarde iCloud.
        if (excludeFromBackup) NSURL.fileURLWithPath(base).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        return base
    }

    override fun appDatabase(): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder<AppDatabase>(name = "$filesDir/mes-courses.db")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)

    override fun catalogDatabase(): RoomDatabase.Builder<CatalogDatabase> =
        Room.databaseBuilder<CatalogDatabase>(name = "$filesDir/catalogue.db")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)

    override val settingsDataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath(produceFile = { "$filesDir/datastore/settings.preferences_pb".toPath() })
    }

    override fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin, config)

    override fun webPageFetcher(): PageFetcher = WkPageFetcher()

    override fun llmEngine(): LlmEngine = nativeLlm?.let { IosLlmEngine(it, cacheDir) } ?: UnavailableLlm

    override fun textEmbedder(modelPath: String): AppTextEmbedder =
        nativeEmbedder?.let { IosTextEmbedder(it, modelPath, cacheDir) } ?: UnavailableEmbedder

    override val downloader: FileDownloader = KtorFileDownloader(
        HttpClient(Darwin) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
        },
    )

    override fun retailerBridge(
        site: RetailerSite,
        uaWithoutWv: () -> Boolean,
        leclercUaMode: () -> String,
        onRequestSeen: ((url: String, headers: Map<String, String>) -> Unit)?,
    ): RetailerBridge = WkRetailerBridge(site, leclercUaMode, onRequestSeen)

    override val cookies: CookieJar = IosCookieJar(scope)

    // --- Réseau (NWPathMonitor) ---
    @Volatile private var online = true
    @Volatile private var expensive = false

    init {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            online = nw_path_get_status(path) == nw_path_status_satisfied
            expensive = nw_path_is_expensive(path)
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    override fun isOnline(): Boolean = online
    override fun isMetered(): Boolean = expensive

    /** iOS n'a pas de service au premier plan : l'app obtient quelques dizaines de secondes une fois quittée. */
    override val background: BackgroundWork = object : BackgroundWork {
        private var task: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid

        override fun start(text: String) {
            scope.launchMain {
                if (task == UIBackgroundTaskInvalid) {
                    task = UIApplication.sharedApplication.beginBackgroundTaskWithName("chef") { endTask() }
                }
            }
        }

        override fun update(text: String) = Unit

        override fun stop() = scope.launchMain { endTask() }

        private fun endTask() {
            if (task != UIBackgroundTaskInvalid) {
                UIApplication.sharedApplication.endBackgroundTask(task)
                task = UIBackgroundTaskInvalid
            }
        }
    }

    /** Pas de mise à jour par l'app elle-même : c'est le système (ou Sideloadly) qui installe. */
    override fun appUpdater(scope: CoroutineScope, http: HttpClient): AppUpdates? = null

    override suspend fun copyPickedFile(file: PickedFile, targetDir: String): String = withContext(Dispatchers.IO) {
        val source = file.handle as NSURL
        val name = (file.name ?: source.lastPathComponent)?.takeIf { it.endsWith(".litertlm") } ?: "modele-importe.litertlm"
        val target = NSURL.fileURLWithPath("$targetDir/$name")
        val scoped = source.startAccessingSecurityScopedResource()
        try {
            NSFileManager.defaultManager.removeItemAtURL(target, error = null)
            check(NSFileManager.defaultManager.copyItemAtURL(source, toURL = target, error = null)) { "Fichier illisible" }
        } finally {
            if (scoped) source.stopAccessingSecurityScopedResource()
        }
        "$targetDir/$name"
    }

    override suspend fun geocode(postalCode: String): GeoPlace? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            CLGeocoder().geocodeAddressString("$postalCode, France") { placemarks, _ ->
                val place = placemarks?.firstOrNull() as? CLPlacemark
                val result = place?.location?.coordinate?.useContents {
                    GeoPlace(latitude, longitude, place.locality ?: "")
                }
                if (cont.isActive) cont.resume(result)
            }
        }
    }

    /** iOS ne dit pas quelles apps sont installées sans déclaration préalable : le bouton est masqué. */
    override fun retailerAppAvailable(retailer: Retailer): Boolean = false

    override fun openRetailerApp(retailer: Retailer) = Unit

    override fun defaultWebUserAgent(): String = IosWeb.mobileSafari

    override fun shareFile(path: String, mimeType: String, title: String) {
        scope.launchMain {
            val controller = UIActivityViewController(listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
            topViewController()?.presentViewController(controller, animated = true, completion = null)
        }
    }
}

/** Écran le plus en avant (pour y présenter une feuille du système). */
internal fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

private fun CoroutineScope.launchMain(block: () -> Unit) {
    launch(Dispatchers.Main) { block() }
}

/** Sans moteur Swift : l'assistant reste indisponible (message clair), le reste de l'app fonctionne. */
private object UnavailableLlm : LlmEngine {
    private const val MESSAGE = "L'assistant n'est pas disponible dans cette version de l'app."
    override suspend fun load(modelPath: String, preferGpu: Boolean, maxTokens: Int?): String = error(MESSAGE)
    override fun maxTokens(): Int = 4096
    override fun isLoaded(): Boolean = false
    override fun newConversation(systemPrompt: String, tools: List<AgentTool>, initial: List<Msg>, prefill: Boolean, maxOutputTokens: Int?) = error(MESSAGE)
    override fun send(message: String): Flow<ChatEvent> = flow { error(MESSAGE) }
    override fun hasConversation(): Boolean = false
    override fun tokenCount(): Int? = null
    override fun cancel() = Unit
    override suspend fun generateOnce(systemPrompt: String, prompt: String, jsonSchema: String?, maxOutputTokens: Int?): String = error(MESSAGE)
    override fun close() = Unit
}

private object UnavailableEmbedder : AppTextEmbedder {
    override val loaded: Boolean = false
    override fun release() = Unit
    override suspend fun available(): Boolean = false
    override suspend fun embedQuery(text: String): ByteArray? = null
    override suspend fun embedPassages(texts: List<String>): List<ByteArray>? = null
}
