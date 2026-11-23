package fr.mescourses.app.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import fr.mescourses.app.data.AppDatabase
import fr.mescourses.app.data.CatalogDatabase
import fr.mescourses.app.llm.LlmEngine
import fr.mescourses.app.retail.RetailerBridge
import fr.mescourses.app.retail.RetailerSite
import fr.mescourses.core.catalog.TextEmbedder
import fr.mescourses.core.recipes.PageFetcher
import fr.mescourses.core.retail.Retailer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlinx.coroutines.CoroutineScope

/**
 * Ce que chaque système (Android, iOS) fournit au code partagé. Tout le reste de l'application
 * (écrans, ViewModels, données, conversation avec le modèle) est commun.
 */
interface Platform {
    val kind: PlatformKind

    /** Dossier privé des fichiers durables (modèles, embedder). */
    val filesDir: String

    /** Dossier des fichiers temporaires (téléchargements des bases, exports). */
    val cacheDir: String

    fun appDatabase(): RoomDatabase.Builder<AppDatabase>
    fun catalogDatabase(): RoomDatabase.Builder<CatalogDatabase>
    val settingsDataStore: DataStore<Preferences>

    /** Client HTTP du système (Android : CIO ; iOS : Darwin). */
    fun httpClient(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient

    /** Navigateur invisible, second recours quand un site refuse le client HTTP. */
    fun webPageFetcher(): PageFetcher

    /** Moteur du LLM local. */
    fun llmEngine(): LlmEngine

    /** Modèle d'embeddings sur l'appareil, lu dans [modelPath]. */
    fun textEmbedder(modelPath: String): AppTextEmbedder

    /** Téléchargements reprenables (modèles, embedder). */
    val downloader: FileDownloader

    /** Navigateur invisible connecté à une enseigne : les requêtes du drive passent par lui. */
    fun retailerBridge(
        site: RetailerSite,
        uaWithoutWv: () -> Boolean,
        leclercUaMode: () -> String,
        onRequestSeen: ((url: String, headers: Map<String, String>) -> Unit)?,
    ): RetailerBridge

    /** Cookies des navigateurs de l'app (communs à toutes les enseignes). */
    val cookies: CookieJar

    fun isOnline(): Boolean

    /** Connexion facturée au volume (données mobiles) : on évite les gros téléchargements automatiques. */
    fun isMetered(): Boolean

    /** Garde l'app éveillée pendant un travail long du chef (Android : service au premier plan). */
    val background: BackgroundWork

    /** Mise à jour de l'app elle-même ; null là où le système s'en charge (iOS). */
    fun appUpdater(scope: CoroutineScope, http: HttpClient): AppUpdates?

    /** Copie dans [targetDir] un fichier choisi par l'utilisateur ; rend son chemin. */
    suspend fun copyPickedFile(file: PickedFile, targetDir: String): String

    /** Lieu d'un code postal français (coordonnées et commune), ou null. */
    suspend fun geocode(postalCode: String): GeoPlace?

    /** L'application officielle de l'enseigne est installée et peut être ouverte. */
    fun retailerAppAvailable(retailer: Retailer): Boolean

    /** Ouvre l'application officielle de l'enseigne. */
    fun openRetailerApp(retailer: Retailer)

    /** User-Agent du navigateur intégré du système (base du User-Agent « Chrome » des requêtes d'images). */
    fun defaultWebUserAgent(): String

    /** Propose de partager un fichier (feuille de partage du système). */
    fun shareFile(path: String, mimeType: String, title: String)
}

enum class PlatformKind { ANDROID, IOS }

data class GeoPlace(val latitude: Double, val longitude: Double, val locality: String)

/** Embedder qu'on peut libérer quand la mémoire manque (il est rechargé à la demande). */
interface AppTextEmbedder : TextEmbedder {
    val loaded: Boolean
    fun release()
}

/** Fichier choisi dans le sélecteur du système (Uri sur Android, URL de fichier sur iOS). */
class PickedFile(val handle: Any, val name: String?)

fun interface FileDownloader {
    /**
     * Téléchargement reprenable (en-tête `Range`, fichier `.part`) avec empreinte SHA-256 optionnelle ;
     * le jeton Hugging Face n'est envoyé qu'aux hôtes huggingface.co. Rend le chemin du fichier.
     */
    suspend fun downloadResumable(
        url: String,
        targetPath: String,
        hfToken: String,
        expectedSha256: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String
}

interface CookieJar {
    /** Vrai si un cookie [name] existe pour l'adresse [url] (lecture immédiate ; jamais de valeur lue). */
    fun has(url: String, name: String): Boolean

    /** Écrit sur disque les cookies en mémoire. */
    fun flush()

    /** Efface tous les cookies (déconnexion de toutes les enseignes). */
    fun clearAll()
}

interface BackgroundWork {
    fun start(text: String)
    fun update(text: String)
    fun stop()
}
