package fr.mescourses.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.DialogProperties
import fr.mescourses.app.AppContainer
import fr.mescourses.app.platform.PickedFile
import fr.mescourses.app.platform.topViewController
import fr.mescourses.app.retail.WkRetailerBridge
import fr.mescourses.core.retail.Retailer
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject

/** iOS : pas de bouton « retour » du système ; chaque écran a sa flèche de retour dans sa barre. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun rememberNotificationPermission(): () -> Unit {
    var asked by rememberSaveable { mutableStateOf(false) }
    return {
        if (!asked) {
            asked = true
            UNUserNotificationCenter.currentNotificationCenter()
                .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
        }
    }
}

@Composable
actual fun rememberModelFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            var callback: (PickedFile) -> Unit = {}
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                callback(PickedFile(url, url.lastPathComponent))
            }
        }
    }
    delegate.callback = onPicked
    return {
        // asCopy : iOS copie le fichier dans l'app, sans droit d'accès à garder.
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun HiddenRetailerView(container: AppContainer, retailer: Retailer, modifier: Modifier) {
    val bridge = container.bridge(retailer) as WkRetailerBridge
    UIKitView(factory = { bridge.view() }, modifier = modifier)
}

actual fun fullScreenDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
