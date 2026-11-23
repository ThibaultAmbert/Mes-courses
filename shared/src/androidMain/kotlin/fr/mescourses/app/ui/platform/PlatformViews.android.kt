package fr.mescourses.app.ui.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import fr.mescourses.app.AppContainer
import fr.mescourses.app.platform.PickedFile
import fr.mescourses.app.retail.RetailerWebViewBridge
import fr.mescourses.core.retail.Retailer

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

/**
 * Android 13+ : demande (une fois) le droit d'afficher la notification « le chef travaille », qui permet
 * de continuer quand on quitte l'app. Sans ce droit, le travail continue quand même, sans notification visible.
 */
@Composable
actual fun rememberNotificationPermission(): () -> Unit {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
actual fun rememberModelFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(PickedFile(uri, uri.lastPathSegment))
    }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
actual fun HiddenRetailerView(container: AppContainer, retailer: Retailer, modifier: Modifier) {
    val bridge = container.bridge(retailer) as RetailerWebViewBridge
    AndroidView(
        factory = { ctx -> bridge.createWebView(ctx) },
        onRelease = { bridge.detach(it) },
        modifier = modifier,
    )
}

actual fun fullScreenDialogProperties(): androidx.compose.ui.window.DialogProperties =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
