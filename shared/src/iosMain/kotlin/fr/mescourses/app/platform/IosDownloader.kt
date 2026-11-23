package fr.mescourses.app.platform

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.HashingSink
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer
import okio.use
import kotlin.coroutines.coroutineContext

/**
 * Téléchargement reprenable (en-tête `Range`, fichier `.part`, redirections suivies à la main pour garder l'en-tête,
 * jeton Hugging Face réservé aux hôtes huggingface.co), avec empreinte SHA-256 optionnelle : même contrat que la
 * version Android.
 */
class KtorFileDownloader(private val client: HttpClient) : FileDownloader {
    private val fs = FileSystem.SYSTEM

    override suspend fun downloadResumable(
        url: String,
        targetPath: String,
        hfToken: String,
        expectedSha256: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val target = targetPath.toPath()
        target.parent?.let { fs.createDirectories(it) }
        val part = "$targetPath.part".toPath()
        var outcome = transfer(url, hfToken, part, onProgress)
        if (outcome == RANGE_REFUSED) {
            fs.delete(part, mustExist = false)
            outcome = transfer(url, hfToken, part, onProgress)
        }
        val (downloaded, total) = outcome.split(':').map { it.toLong() }
        if (total > 0 && downloaded < total) error("Téléchargement incomplet ($downloaded / $total octets)")
        if (expectedSha256.isNotBlank()) {
            val actual = sha256(part)
            if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                fs.delete(part, mustExist = false)
                error("Fichier corrompu : empreinte SHA-256 invalide (attendu ${expectedSha256.take(12)}…, reçu ${actual.take(12)}…)")
            }
        }
        fs.delete(target, mustExist = false)
        fs.atomicMove(part, target)
        onProgress(downloaded, total)
        targetPath
    }

    /** Rend « téléchargés:total », ou [RANGE_REFUSED] si le serveur refuse la reprise (416). */
    private suspend fun transfer(url: String, hfToken: String, part: okio.Path, onProgress: (Long, Long) -> Unit): String {
        val offset = fs.metadataOrNull(part)?.size ?: 0L
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val token = if (Url(current).host.endsWith("huggingface.co")) hfToken else ""
            val outcome = client.prepareGet(current) {
                if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
                if (offset > 0) header(HttpHeaders.Range, "bytes=$offset-")
            }.execute { response ->
                val code = response.status.value
                when {
                    code in 300..399 -> response.headers[HttpHeaders.Location]?.let { "→" + Url(current).resolve(it) }
                        ?: error("Redirection sans adresse")
                    code == 416 && offset > 0 -> RANGE_REFUSED
                    code == 401 || code == 403 -> error("Accès refusé ($code) : accepte la licence Gemma sur Hugging Face et renseigne ton jeton HF.")
                    code !in 200..299 -> error("Erreur HTTP $code")
                    else -> {
                        val append = code == 206
                        if (!append) fs.delete(part, mustExist = false)
                        val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
                        val total = (if (append) offset else 0L) + length
                        var downloaded = if (append) offset else 0L
                        onProgress(downloaded, total)
                        val channel = response.bodyAsChannel()
                        val sink = if (append) fs.appendingSink(part) else fs.sink(part)
                        sink.buffer().use { out ->
                            val buffer = ByteArray(1 shl 16)
                            var lastEmit = downloaded
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = channel.readAvailable(buffer, 0, buffer.size)
                                if (n < 0) break
                                if (n == 0) continue
                                out.write(buffer, 0, n)
                                downloaded += n
                                if (downloaded - lastEmit > 1_000_000) {
                                    lastEmit = downloaded
                                    onProgress(downloaded, total)
                                }
                            }
                        }
                        "$downloaded:$total"
                    }
                }
            }
            if (!outcome.startsWith("→")) return outcome
            current = outcome.removePrefix("→")
        }
        error("Trop de redirections")
    }

    private fun sha256(path: okio.Path): String {
        val hashing = HashingSink.sha256(blackholeSink())
        hashing.buffer().use { out -> fs.source(path).use { out.writeAll(it) } }
        return hashing.hash.hex()
    }

    private fun Url.resolve(location: String): String = fr.mescourses.core.resolveUrl(toString(), location)

    private companion object {
        const val RANGE_REFUSED = "416"
        const val MAX_REDIRECTS = 5
    }
}
