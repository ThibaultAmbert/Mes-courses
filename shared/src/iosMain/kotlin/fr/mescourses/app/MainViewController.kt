package fr.mescourses.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import fr.mescourses.app.llm.NativeEmbedder
import fr.mescourses.app.llm.NativeLlm
import fr.mescourses.app.platform.IosPlatform
import fr.mescourses.app.ui.AppRoot
import fr.mescourses.app.ui.common.LocalAppContainer
import fr.mescourses.app.ui.theme.MesCoursesTheme
import fr.mescourses.app.ui.theme.darkFor
import fr.mescourses.core.recipes.RecipeSites
import kotlinx.coroutines.flow.MutableStateFlow
import platform.UIKit.UIViewController

/** État de l'app iOS, créé une fois au lancement (équivalent de MesCoursesApp sur Android). */
object IosApp {
    private var instance: AppContainer? = null

    /** Adresse de recette reçue par « Partager » ou par le lien mescourses://import?url=… */
    val sharedUrl = MutableStateFlow<String?>(null)

    fun container(llm: NativeLlm?, embedder: NativeEmbedder?): AppContainer =
        instance ?: AppContainer(IosPlatform(llm, embedder)).also { instance = it }

    /** Texte ou lien reçu de l'extension de partage / du schéma d'URL : on garde la première adresse web. */
    fun handleIncoming(text: String) {
        val decoded = text.substringAfter("url=", text).let { runCatching { decodeUrlComponent(it) }.getOrDefault(it) }
        RecipeSites.findUrl(decoded)?.let { sharedUrl.value = it }
    }

    /** Le système manque de mémoire : on libère le modèle et l'embedder (rechargés à la demande). */
    fun onMemoryWarning() {
        instance?.let {
            it.model.releaseIfIdle()
            it.embedder.release()
        }
    }

    private fun decodeUrlComponent(s: String): String {
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '%' && i + 2 < s.length + 0 && i + 2 <= s.lastIndex -> {
                    bytes += s.substring(i + 1, i + 3).toInt(16).toByte(); i += 3
                }
                ch == '+' -> { bytes += ' '.code.toByte(); i++ }
                else -> { ch.toString().encodeToByteArray().forEach { bytes += it }; i++ }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}

/** Écran racine de l'app iOS, appelé depuis Swift avec les moteurs LiteRT-LM (ou null pour s'en passer). */
fun MainViewController(llm: NativeLlm?, embedder: NativeEmbedder?): UIViewController {
    val container = IosApp.container(llm, embedder)
    return ComposeUIViewController {
        val settings by container.settings.state.collectAsState()
        val shared by IosApp.sharedUrl.collectAsState()
        CompositionLocalProvider(LocalAppContainer provides container) {
            MesCoursesTheme(dark = darkFor(settings.theme)) {
                AppRoot(sharedUrl = shared, onSharedUrlConsumed = { IosApp.sharedUrl.value = null })
            }
        }
    }
}
