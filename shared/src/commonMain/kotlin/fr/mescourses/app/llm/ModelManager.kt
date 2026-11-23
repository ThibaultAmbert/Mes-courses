package fr.mescourses.app.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.IO
import fr.mescourses.app.platform.LocalFile
import fr.mescourses.app.platform.PickedFile
import fr.mescourses.app.platform.Platform
import fr.mescourses.app.platform.freeSpaceBytes

data class ModelInfo(
    val id: String,
    val label: String,
    val description: String,
    val fileName: String,
    val url: String,
    val approxSizeGb: Double,
)

/**
 * Modèles conseillés pour le Honor Magic 5 Pro (Snapdragon 8 Gen 2, 12 Go).
 * Les fichiers .litertlm sont publiés par la communauté LiteRT sur Hugging Face ;
 * si une adresse change, on peut la corriger dans Réglages ou importer le fichier à la main.
 */
object ModelCatalog {
    val models = listOf(
        ModelInfo(
            id = "gemma4-e4b",
            label = "Gemma 4 E4B (recommandé)",
            description = "Meilleure qualité, très bon français et appels d'outils. ~10-14 mots/s sur GPU.",
            fileName = "gemma-4-E4B-it.litertlm",
            url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            approxSizeGb = 3.6,
        ),
        ModelInfo(
            id = "gemma4-e2b",
            label = "Gemma 4 E2B (rapide)",
            description = "Deux fois plus rapide, un peu moins fiable sur les outils.",
            fileName = "gemma-4-E2B-it.litertlm",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            approxSizeGb = 2.0,
        ),
    )
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val label: String, val downloaded: Long, val total: Long) : DownloadState {
        val progress: Float get() = if (total > 0) downloaded.toFloat() / total else 0f
    }
    data class Done(val path: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

class ModelManager(private val platform: Platform, private val scope: CoroutineScope) {

    val modelsDir: LocalFile get() = LocalFile(LocalFile(platform.filesDir), "models").mkdirs()

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state
    private var job: Job? = null

    fun installed(): List<LocalFile> = modelsDir.listFiles().filter { it.extension == "litertlm" }.sortedBy { it.name }

    fun freeSpaceGb(): Double = freeSpaceBytes(modelsDir.path) / 1e9

    /** Téléchargement en arrière-plan (reprise possible grâce au fichier .part). */
    fun download(
        model: ModelInfo,
        url: String = model.url,
        hfToken: String = "",
        dir: LocalFile = modelsDir,
        expectedSha256: String = "",
        onDone: (String) -> Unit,
    ) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val target = LocalFile(dir, model.fileName)
            try {
                platform.downloader.downloadResumable(url, target.path, hfToken, expectedSha256) { downloaded, total ->
                    _state.value = DownloadState.Running(model.label, downloaded, total)
                }
                _state.value = DownloadState.Done(target.absolutePath)
                withContext(Dispatchers.Main) { onDone(target.absolutePath) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DownloadState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = DownloadState.Idle
    }

    /** Import d'un fichier .litertlm déjà présent sur l'appareil (téléchargé depuis un PC, par ex.). */
    suspend fun import(file: PickedFile): String {
        _state.value = DownloadState.Running("Import", 0, 0)
        val path = platform.copyPickedFile(file, modelsDir.path)
        _state.value = DownloadState.Done(path)
        return path
    }

    fun delete(file: LocalFile) { file.delete() }
}
