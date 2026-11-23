package fr.mescourses.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import fr.mescourses.app.ui.AppRoot
import fr.mescourses.app.ui.common.LocalAppContainer
import fr.mescourses.app.ui.theme.MesCoursesTheme
import fr.mescourses.app.ui.theme.darkFor

class MainActivity : ComponentActivity() {

    /** URL reçue via « Partager » depuis le navigateur (import de recette). */
    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Après une recréation (rotation, retour de l'arrière-plan), le partage a déjà été traité.
        if (savedInstanceState == null) handle(intent)
        val container = (application as MesCoursesApp).container
        val settings = container.settings
        setContent {
            val mode = settings.state.collectAsState().value.theme
            val dark = darkFor(mode)
            // Barres système accordées au choix Jour / Nuit (et pas seulement au réglage du téléphone).
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                MesCoursesTheme(dark = dark) {
                    AppRoot(sharedUrl = sharedUrl.value, onSharedUrlConsumed = { sharedUrl.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            sharedUrl.value = Regex("https?://\\S+").find(text)?.value
            // L'intent est consommé : il ne doit pas relancer l'import une seconde fois.
            setIntent(Intent(this, MainActivity::class.java))
        }
    }
}
