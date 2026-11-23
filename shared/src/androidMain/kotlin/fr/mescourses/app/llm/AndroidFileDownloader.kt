package fr.mescourses.app.llm

import android.content.Context
import android.net.Uri
import fr.mescourses.app.platform.FileDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Téléchargements reprenables d'Android (code d'origine, inchangé). */
object AndroidFileDownloader : FileDownloader {
    override suspend fun downloadResumable(
        url: String,
        targetPath: String,
        hfToken: String,
        expectedSha256: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): String = downloadResumable(url, File(targetPath), hfToken, expectedSha256, onProgress).absolutePath

        /**
         * Téléchargement reprenable (en-tête HTTP `Range`, fichier `.part`, suivi manuel des redirections) avec
         * vérification optionnelle de l'empreinte SHA-256 avant renommage vers [target].
         */
        suspend fun downloadResumable(
            url: String,
            target: File,
            hfToken: String = "",
            expectedSha256: String = "",
            onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        ): File = withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            var connection = openFollowingRedirects(url, hfToken, part.length())
            try {
                if (connection.responseCode == 416 && part.exists()) {
                    connection.disconnect()
                    part.delete()
                    connection = openFollowingRedirects(url, hfToken, 0L)
                }
                val code = connection.responseCode
                if (code == 401 || code == 403) error("Accès refusé ($code) : accepte la licence Gemma sur Hugging Face et renseigne ton jeton HF.")
                if (code !in 200..299) error("Erreur HTTP $code")
                val append = code == 206
                if (!append) part.delete()
                val total = (if (append) part.length() else 0L) + connection.contentLengthLong.coerceAtLeast(0)
                var downloaded = if (append) part.length() else 0L
                onProgress(downloaded, total)
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(part, append).use { out ->
                        val buffer = ByteArray(1 shl 16)
                        var lastEmit = downloaded
                        while (isActive) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            downloaded += n
                            if (downloaded - lastEmit > 1_000_000) {
                                lastEmit = downloaded
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }
                ensureActive()
                if (total > 0 && downloaded < total) error("Téléchargement incomplet ($downloaded / $total octets)")
                if (expectedSha256.isNotBlank()) {
                    val actual = sha256(part)
                    if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                        part.delete()
                        error("Fichier corrompu : empreinte SHA-256 invalide (attendu ${expectedSha256.take(12)}…, reçu ${actual.take(12)}…)")
                    }
                }
                target.delete()
                if (!part.renameTo(target)) {
                    part.delete()
                    error("Impossible d'enregistrer ${target.name}")
                }
                onProgress(downloaded, total)
                target
            } finally {
                connection.disconnect()
            }
        }

        private fun openFollowingRedirects(url: String, hfToken: String, offset: Long): HttpURLConnection {
            var currentUrl = url
            var connection = openConnection(currentUrl, hfToken, offset)
            // Hugging Face redirige vers un CDN : on suit manuellement pour garder l'en-tête Range.
            var redirects = 0
            while (connection.responseCode in 300..399 && redirects++ < 5) {
                val next = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                currentUrl = URL(URL(currentUrl), next).toString()
                val tokenForHost = if (URL(currentUrl).host.endsWith("huggingface.co")) hfToken else ""
                connection = openConnection(currentUrl, tokenForHost, offset)
            }
            return connection
        }

        private fun openConnection(url: String, token: String, offset: Long): HttpURLConnection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 60_000
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    
}

/** Import d'un fichier .litertlm déjà présent sur le téléphone (Uri du sélecteur de fichiers). */
suspend fun copyUriToDir(context: Context, uri: Uri, targetDir: String): String = withContext(Dispatchers.IO) {
    val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.endsWith(".litertlm") } ?: "modele-importe.litertlm"
    val target = File(targetDir, name)
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Fichier illisible" }
        target.outputStream().use { input.copyTo(it, 1 shl 16) }
    }
    target.absolutePath
}
