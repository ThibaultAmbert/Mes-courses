package fr.mescourses.app.data

import fr.mescourses.app.platform.AppTextEmbedder
import fr.mescourses.app.platform.LocalFile
import fr.mescourses.app.platform.Log
import fr.mescourses.app.platform.Platform
import fr.mescourses.app.platform.formatThousands
import fr.mescourses.core.catalog.CatalogEntry
import fr.mescourses.core.catalog.CatalogSources
import fr.mescourses.core.catalog.CatalogUpdater
import fr.mescourses.core.catalog.EmbeddingText
import fr.mescourses.core.catalog.TextEmbedder
import fr.mescourses.core.recipes.RecipeSites
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.gzip
import okio.use
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Base d'un site telle que la release « catalogue » la publie. */
data class RemoteSite(val url: String, val bytes: Long)

/** Métadonnées de l'embedder publiées par `embedder.json` dans la release « catalogue ». */
@Serializable
data class EmbedderRemote(
    val model: String = CatalogSync.DEFAULT_EMBEDDER_MODEL,
    val file: String = CatalogSync.DEFAULT_EMBEDDER_FILE,
    val dim: Int = 256,
    val textVersion: Int = 1,
    val scale: Float = 0.25f,
    val url: String = CatalogSync.DEFAULT_EMBEDDER_URL,
    val bytes: Long = CatalogSync.DEFAULT_EMBEDDER_BYTES,
    val sha256: String = "",
)

/** État du catalogue affiché dans les Réglages et dans l'écran de choix des bases. */
data class CatalogStatus(
    val count: Int = 0,
    val bySite: Map<String, Int> = emptyMap(),
    /** Date (ms) de la dernière recherche de nouveautés ; null = jamais. */
    val lastUpdate: Long? = null,
    /** Travail en cours (« Nouveautés de Marmiton… ») ; null = au repos. */
    val working: String? = null,
    val lastResult: String? = null,
    /** L'état a été lu dans la base au moins une fois (avant, « aucune base » ne veut encore rien dire). */
    val loaded: Boolean = false,
    /** Sites dont la base est sur le téléphone. */
    val installed: Set<String> = emptySet(),
    /** Version (date de publication) de la base de chaque site installé. */
    val versions: Map<String, String> = emptyMap(),
    /** Dernière version publiée ; null = pas encore cherchée ou hors ligne. */
    val remoteVersion: String? = null,
    /** Bases disponibles au téléchargement (taille comprise) ; vide = pas encore cherchées. */
    val remote: Map<String, RemoteSite> = emptyMap(),
    /** Site en cours de téléchargement. */
    val installing: String? = null,
    /** Avancement du téléchargement de la base en cours (0..1) ; null = inconnu ou pendant l'enregistrement. */
    val installProgress: Float? = null,
    /** Sites en attente de leur tour (téléchargements demandés pendant qu'un autre est en cours). */
    val pending: Set<String> = emptySet(),
    /** Métadonnées du modèle d'embeddings publiées par la release (ou repli par défaut). */
    val embedderRemote: EmbedderRemote? = null,
    /** Le fichier du modèle d'embeddings est présent et non vide dans `files/embedder/`. */
    val embedderInstalled: Boolean = false,
    /** Téléchargement du modèle d'embeddings en cours. */
    val embedderDownloading: Boolean = false,
    val embedderDownloaded: Long = 0L,
    val embedderTotal: Long = 0L,
    val embedderError: String? = null,
) {
    /** Une base installée dont une version plus récente est publiée. */
    fun outdated(siteId: String): Boolean {
        val mine = versions[siteId]
        return siteId in installed && remoteVersion != null && siteId in remote && mine != remoteVersion
    }
}

/**
 * Les bases de recettes (une par site) : chacune se télécharge à la demande depuis la release « catalogue » du dépôt
 * (refaite chaque jour par la CI), sans être embarquée dans l'APK. Au lancement (hors données mobiles) ou sur demande,
 * l'appli vérifie d'abord s'il existe une base CI plus récente pour les sites installés, l'importe, puis complète en
 * scannant les nouveautés sur les sites (au plus une fois par jour en automatique).
 */
class CatalogSync(
    private val platform: Platform,
    private val store: RoomCatalogStore,
    private val scope: CoroutineScope,
    private val http: HttpClient,
    private val embedder: AppTextEmbedder? = null,
) {
    val status = MutableStateFlow(CatalogStatus())

    /** Écran de choix à montrer au premier lancement : null = pas encore su, true = à montrer, false = déjà vu. */
    val firstRun = MutableStateFlow<Boolean?>(null)

    private val lock = Mutex()
    private val embedderLock = Mutex()
    private var embedderJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val updater = CatalogUpdater(store, ::fetch)

    @Serializable private data class Asset(val name: String = "", val size: Long = 0, val browser_download_url: String = "")
    @Serializable private data class Release(val assets: List<Asset> = emptyList())

    /** Au lancement de l'appli. Ne bloque rien : tout se passe en arrière-plan. */
    fun start() {
        scope.launch(Dispatchers.IO) {
            refresh(null)
            val s = status.value
            // Qui a déjà des recettes (version précédente de l'appli) n'a pas besoin de l'écran de choix.
            if (s.count > 0 && store.meta(SETUP_DONE) == null) store.setMeta(SETUP_DONE, "1")
            firstRun.value = store.meta(SETUP_DONE) == null
            // Laisse l'appli démarrer avant de solliciter le réseau ; pas de mise à jour d'office en données mobiles.
            delay(15_000)
            if (!metered()) {
                loadRemote()
                val current = status.value
                val hasOutdated = current.installed.any { current.outdated(it) }
                if (current.installed.isNotEmpty() && (hasOutdated || updater.due())) {
                    syncAndUpdate(forceCrawl = false)
                }
            }
        }
    }

    /** L'écran de choix a été vu (ou passé) : il ne s'ouvre plus tout seul. */
    fun finishSetup() {
        firstRun.value = false
        scope.launch(Dispatchers.IO) { store.setMeta(SETUP_DONE, "1") }
    }

    /** Cherche ce que la release publie (à l'ouverture de l'écran de choix). */
    fun checkRemote() {
        scope.launch(Dispatchers.IO) { loadRemote() }
    }

    /** Télécharge et installe les bases demandées, l'une après l'autre (les demandes se mettent en file). */
    fun install(siteIds: List<String>) {
        val ids = siteIds.filter { CatalogSources.byId(it) != null }.distinct()
            .filter { it != status.value.installing && it !in status.value.pending }
        if (ids.isEmpty()) return
        status.update { it.copy(pending = it.pending + ids) }
        scope.launch(Dispatchers.IO) {
            exclusive("Téléchargement…", wait = true) {
                val results = ArrayList<String>()
                val failed = ArrayList<String>()
                for (id in ids) {
                    val name = siteName(id)
                    status.update { it.copy(installing = id, installProgress = null, pending = it.pending - id, working = "$name : téléchargement…") }
                    try {
                        results += "$name : ${formatThousands(installOne(id))}"
                    } catch (e: CancellationException) {
                        status.update { it.copy(pending = it.pending - ids.toSet()) }
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Base $id", e)
                        failed += "$name (${e.message?.take(80)})"
                    }
                    refresh(null)
                }
                listOfNotNull(
                    results.takeIf { it.isNotEmpty() }?.let { "Téléchargé · ${it.joinToString(" · ")}" },
                    failed.takeIf { it.isNotEmpty() }?.let { "Échec : ${it.joinToString(", ")}" },
                ).joinToString(" — ")
            }
        }
    }

    /** Retire la base d'un site du téléphone. */
    fun remove(siteId: String) {
        scope.launch(Dispatchers.IO) {
            exclusive("Suppression…") {
                store.deleteSite(siteId)
                store.deleteMeta(versionKey(siteId))
                "${siteName(siteId)} supprimée"
            }
        }
    }

    /** Bouton « Chercher les nouveautés maintenant ». */
    fun updateNow() {
        scope.launch(Dispatchers.IO) { syncAndUpdate(forceCrawl = true) }
    }

    /** Télécharge le modèle d'embeddings (reprise possible via `.part`, vérifié par SHA-256). */
    fun installEmbedder(onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }) {
        if (embedderJob?.isActive == true) return
        embedderJob = scope.launch(Dispatchers.IO) {
            embedderLock.withLock {
                status.update {
                    it.copy(
                        embedderDownloading = true,
                        embedderDownloaded = 0L,
                        embedderTotal = it.embedderRemote?.bytes?.takeIf { b -> b > 0 } ?: DEFAULT_EMBEDDER_BYTES,
                        embedderError = null,
                    )
                }
                try {
                    if (status.value.embedderRemote == null) loadRemote()
                    val remote = status.value.embedderRemote ?: EmbedderRemote()
                    val target = embedderFile()
                    platform.downloader.downloadResumable(
                        url = remote.url.ifBlank { DEFAULT_EMBEDDER_URL },
                        targetPath = target.path,
                        hfToken = "",
                        expectedSha256 = remote.sha256,
                    ) { downloaded, total ->
                        val effectiveTotal = if (total > 0) total else remote.bytes.takeIf { it > 0 } ?: DEFAULT_EMBEDDER_BYTES
                        status.update { it.copy(embedderDownloaded = downloaded, embedderTotal = effectiveTotal) }
                        onProgress(downloaded, effectiveTotal)
                    }
                    status.update {
                        it.copy(
                            embedderInstalled = isEmbedderInstalled(),
                            embedderDownloading = false,
                            embedderDownloaded = target.length(),
                            embedderTotal = target.length(),
                            embedderError = null,
                        )
                    }
                } catch (e: CancellationException) {
                    status.update { it.copy(embedderDownloading = false) }
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Téléchargement de l'embedder", e)
                    status.update {
                        it.copy(
                            embedderInstalled = isEmbedderInstalled(),
                            embedderDownloading = false,
                            embedderError = e.message ?: e.toString(),
                        )
                    }
                }
            }
        }
    }

    /** Interrompt le téléchargement de l'embedder (conserve le `.part` pour reprendre plus tard). */
    fun cancelEmbedder() {
        embedderJob?.cancel()
        embedderJob = null
        status.update { it.copy(embedderDownloading = false) }
    }

    /** Supprime le modèle d'embeddings (ferme d'abord le moteur s'il est chargé) et met à jour le statut. */
    fun removeEmbedder(embedder: AppTextEmbedder? = null) {
        embedderJob?.cancel()
        embedderJob = null
        scope.launch(Dispatchers.IO) {
            embedderLock.withLock {
                (embedder ?: this@CatalogSync.embedder)?.release()
                val target = embedderFile()
                target.delete()
                LocalFile(target.path + ".part").delete()
                status.update {
                    it.copy(
                        embedderInstalled = isEmbedderInstalled(),
                        embedderDownloading = false,
                        embedderDownloaded = 0L,
                        embedderTotal = 0L,
                        embedderError = null,
                    )
                }
            }
        }
    }

    /**
     * Stratégie CI-first puis scan incrémental :
     * 1. Télécharge d'abord les bases CI plus récentes pour les sites déjà installés ;
     * 2. Si le délai quotidien est écoulé (ou sur demande manuelle), scanne les sites pour les nouveautés publiées
     *    depuis la dernière base CI, puis vectorise localement les fiches ajoutées.
     */
    private suspend fun syncAndUpdate(forceCrawl: Boolean) = exclusive("Vérification du catalogue…") {
        val installed = status.value.installed
        if (installed.isEmpty()) return@exclusive "Aucune base téléchargée"

        if (forceCrawl || status.value.remoteVersion == null) loadRemote()
        val outdated = installed.sorted().filter { status.value.outdated(it) }
        val ciUpdated = ArrayList<String>()
        val ciFailed = ArrayList<String>()
        for (id in outdated) {
            val name = siteName(id)
            status.update { it.copy(installing = id, working = "$name : mise à jour de la base…") }
            try {
                installOne(id)
                ciUpdated += name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Mise à jour CI $id", e)
                ciFailed += name
            }
        }
        status.update { it.copy(installing = null, installProgress = null) }

        val shouldCrawl = forceCrawl || updater.due()
        val report = if (shouldCrawl) {
            updater.update(only = installed) { message -> status.update { it.copy(working = message) } }
        } else null
        val embedded = if (shouldCrawl) embedMissing() else 0

        buildString {
            if (ciUpdated.isNotEmpty()) append("Bases MAJ : ${ciUpdated.joinToString(", ")}")
            if (ciFailed.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("MAJ CI échouée : ${ciFailed.joinToString(", ")}")
            }
            if (report != null) {
                if (isNotEmpty()) append(" · ")
                append(
                    if (report.added == 0) "Aucune nouvelle recette"
                    else "${report.added} nouvelle${if (report.added > 1) "s" else ""} recette${if (report.added > 1) "s" else ""}",
                )
                if (embedded > 0) append(" · $embedded vectorisée${if (embedded > 1) "s" else ""}")
                if (report.failedSites.isNotEmpty()) append(" · sans réponse : ${report.failedSites.joinToString(", ")}")
            }
        }.ifEmpty { "Catalogue à jour" }
    }

    /**
     * Vectorise localement jusqu'à [MAX_EMBED_PER_UPDATE] fiches sans vecteur (typiquement les nouveautés lues par le
     * téléphone). Tourne hors du thread principal par lots de [EMBED_BATCH_SIZE] fiches.
     */
    private suspend fun embedMissing(): Int {
        val textEmbedder = embedder ?: return 0
        return withContext(Dispatchers.IO) {
            if (!textEmbedder.available()) return@withContext 0
            val pending = store.withoutEmbedding(MAX_EMBED_PER_UPDATE)
            if (pending.isEmpty()) return@withContext 0
            var embedded = 0
            try {
                for (chunk in pending.chunked(EMBED_BATCH_SIZE)) {
                    ensureActive()
                    status.update { it.copy(working = "Vectorisation des fiches… ($embedded/${pending.size})") }
                    val texts = chunk.map(::passageText)
                    val vectors = textEmbedder.embedPassages(texts)
                    if (vectors == null || vectors.size != chunk.size) {
                        if (vectors != null) Log.w(TAG, "Lot de ${chunk.size} fiches : ${vectors.size} vecteurs reçus")
                        break
                    }
                    store.setEmbeddings(chunk.indices.map { i -> chunk[i].id to vectors[i] })
                    embedded += chunk.size
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Vectorisation des fiches", e)
            }
            embedded
        }
    }

    private fun passageText(row: IdTitleKeys): String {
        val keys = row.keys.split(',').filter { it.isNotBlank() }
        return EmbeddingText.passage(CatalogEntry(url = "", siteId = "", title = row.title, keys = keys))
    }

    /** Un seul travail à la fois ; l'état est relu à la fin, même en cas d'erreur. */
    private suspend fun exclusive(label: String, wait: Boolean = false, work: suspend () -> String?) {
        if (wait) lock.lock() else if (!lock.tryLock()) return
        try {
            status.update { it.copy(working = label) }
            val result = try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, label, e)
                "Échec : ${e.message}"
            }
            refresh(result)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun refresh(result: String?) {
        val bySite = store.countBySite()
        val installed = bySite.filterValues { it > 0 }.keys
        status.update {
            it.copy(
                count = store.count(),
                bySite = bySite,
                lastUpdate = store.meta(CatalogUpdater.LAST_UPDATE)?.toLongOrNull(),
                working = null,
                installing = null,
                installProgress = null,
                lastResult = result ?: it.lastResult,
                loaded = true,
                installed = installed,
                versions = installed.mapNotNull { id -> storedVersion(id)?.let { v -> id to v } }.toMap(),
                embedderInstalled = isEmbedderInstalled(),
            )
        }
    }

    /** Version d'une base installée ; les bases chargées par une ancienne version de l'appli ont l'ancienne clé. */
    private suspend fun storedVersion(siteId: String): String? =
        store.meta(versionKey(siteId)) ?: store.meta("fichier:$siteId.jsonl.gz") ?: store.meta("fichier:$siteId.jsonl")

    /** Release « catalogue » : liste des bases (adresse, taille), version publiée et métadonnées de l'embedder. */
    private suspend fun loadRemote() {
        try {
            val response = http.get("https://api.github.com/repos/$REPO/releases/tags/$TAG_NAME") { header("Accept", "application/vnd.github+json") }
            check(response.status.isSuccess()) { "GitHub a répondu ${response.status.value}" }
            val release = json.decodeFromString(Release.serializer(), response.body<String>())
            val sites = release.assets
                .filter { it.name.endsWith(".jsonl.gz") && !it.name.endsWith(".source.jsonl.gz") }
                .associate { it.name.removeSuffix(".jsonl.gz") to RemoteSite(it.browser_download_url, it.size) }
            val version = release.assets.firstOrNull { it.name == "version.txt" }?.let {
                http.get(it.browser_download_url).takeIf { r -> r.status.isSuccess() }?.body<String>()?.trim()
            }
            val embedderRemote = release.assets.firstOrNull { it.name == "embedder.json" }?.let { asset ->
                runCatching {
                    val r = http.get(asset.browser_download_url)
                    if (r.status.isSuccess()) json.decodeFromString(EmbedderRemote.serializer(), r.body<String>()) else null
                }.getOrNull()
            } ?: status.value.embedderRemote ?: EmbedderRemote()
            status.update { it.copy(remote = sites, remoteVersion = version, embedderRemote = embedderRemote) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Bases publiées", e)
        }
    }

    /**
     * Installe ou met à jour une base téléchargée depuis la release CI « catalogue ». Renvoie le nombre de recettes.
     */
    private suspend fun installOne(siteId: String): Int = withContext(Dispatchers.IO) {
        if (status.value.remote.isEmpty()) loadRemote()
        val remote = status.value.remote[siteId]
            ?: error("téléchargement depuis la release impossible (vérifie ta connexion)")
        val file = LocalFile(LocalFile(platform.cacheDir).mkdirs(), "base-$siteId.jsonl.gz")
        try {
            download(remote, file) { done ->
                status.update {
                    it.copy(
                        working = "${siteName(siteId)} : téléchargement… (${done / 1_000_000} Mo" + (if (remote.bytes > 0) " sur ${remote.bytes / 1_000_000} Mo)" else ")"),
                        installProgress = if (remote.bytes > 0) (done.toFloat() / remote.bytes).coerceIn(0f, 1f) else null,
                    )
                }
            }
            val importedUrls = importFile(siteId, file)
            check(importedUrls.isNotEmpty()) { "fichier vide" }
            store.pruneSite(siteId, importedUrls)
            store.setMeta(versionKey(siteId), status.value.remoteVersion ?: "inconnue")
            importedUrls.size
        } finally {
            file.delete()
        }
    }

    private suspend fun download(remote: RemoteSite, target: LocalFile, progress: (Long) -> Unit) {
        val response = http.get(remote.url)
        check(response.status.isSuccess()) { "téléchargement refusé (${response.status.value})" }
        var done = 0L
        var next = 0L
        val input = response.bodyAsChannel()
        FileSystem.SYSTEM.sink(target.path.toPath()).buffer().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.readAvailable(buffer, 0, buffer.size)
                if (n < 0) break
                if (n == 0) continue
                out.write(buffer, 0, n)
                done += n
                if (done >= next) { progress(done); next = done + 1_000_000 }
            }
        }
        check(remote.bytes <= 0 || done == remote.bytes) { "fichier incomplet ($done sur ${remote.bytes} octets)" }
    }

    /** Lit un fichier `.jsonl(.gz)` et range ses recettes dans la base ; renvoie les URLs importées. */
    private suspend fun importFile(siteId: String, file: LocalFile): Set<String> {
        val importedUrls = HashSet<String>()
        FileSystem.SYSTEM.source(file.path.toPath()).buffer().use { input ->
            val head = input.peek()
            val gzip = head.request(2) && head.readByte() == 0x1f.toByte() && head.readByte() == 0x8b.toByte()
            val reader = if (gzip) input.gzip().buffer() else input
            reader.use {
                val lines = generateSequence { reader.readUtf8Line() }
                lines.filter { it.isNotBlank() }.chunked(400).forEach { part ->
                    val entries = part.mapNotNull { line -> runCatching { json.decodeFromString(CatalogEntry.serializer(), line) }.getOrNull() }
                        .filter { it.siteId == siteId }
                    store.upsert(entries)
                    entries.forEach { importedUrls += it.url }
                    status.update { it.copy(working = "${siteName(siteId)} : enregistrement… (${formatThousands(importedUrls.size)} recettes)", installProgress = null) }
                }
            }
        }
        return importedUrls
    }

    private fun versionKey(siteId: String) = "version:$siteId"
    private fun siteName(siteId: String) = RecipeSites.byId(siteId)?.name ?: siteId

    /** Texte d'une page ou d'un sitemap (les sitemaps .gz sont décompressés). */
    private suspend fun fetch(url: String): String {
        val response = http.get(url) {
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", "fr-FR,fr;q=0.9")
        }
        check(response.status.isSuccess()) { "La page a répondu ${response.status.value}" }
        val bytes: ByteArray = response.body()
        val plain = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            (Buffer().write(bytes) as okio.Source).gzip().buffer().use { it.readByteArray() }
        } else bytes
        return plain.decodeToString()
    }

    private fun embedderFile(): LocalFile = LocalFile(LocalFile(LocalFile(platform.filesDir), EMBEDDER_DIR).mkdirs(), DEFAULT_EMBEDDER_FILE)

    private fun isEmbedderInstalled(): Boolean = embedderFile().let { it.isFile && it.length() > 0L }

    private fun metered(): Boolean = runCatching { platform.isMetered() }.getOrDefault(false)

    companion object {
        private const val TAG = "CatalogSync"
        private const val REPO = "ThibaultAmbert/Mes-courses"
        private const val TAG_NAME = "catalogue"
        private const val SETUP_DONE = "choix-des-bases-vu"
        private const val MAX_EMBED_PER_UPDATE = 500
        private const val EMBED_BATCH_SIZE = 32
        const val EMBEDDER_DIR = "embedder"
        const val DEFAULT_EMBEDDER_MODEL = "embeddinggemma-2-740m@256"
        const val DEFAULT_EMBEDDER_FILE = "embeddinggemma-2-740m.litertlm"
        const val DEFAULT_EMBEDDER_URL =
            "https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/main/embeddinggemma-2-740m.litertlm"
        const val DEFAULT_EMBEDDER_BYTES = 485_000_000L
    }
}
