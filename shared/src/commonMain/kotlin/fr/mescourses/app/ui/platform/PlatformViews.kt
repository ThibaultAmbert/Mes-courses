package fr.mescourses.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.mescourses.app.AppContainer
import fr.mescourses.app.platform.PickedFile
import fr.mescourses.core.retail.Retailer

/*
 * Points d'interface qui dépendent du système. Android : versions d'origine (WebView, lanceurs d'activité) ;
 * iOS : WKWebView, sélecteur de documents, centre de notifications.
 */

/** Retour arrière du système (bouton ou geste Android ; sans effet sur iOS, où l'écran a son bouton « retour »). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Demande (une fois) le droit d'afficher la notification « le chef travaille ». */
@Composable
expect fun rememberNotificationPermission(): () -> Unit

/** Ouvre le sélecteur de fichiers du système pour importer un modèle (.litertlm). */
@Composable
expect fun rememberModelFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit

/** Navigateur invisible d'une enseigne, attaché à l'écran pour exécuter les requêtes du drive. */
@Composable
expect fun HiddenRetailerView(container: AppContainer, retailer: Retailer, modifier: Modifier)

enum class WebMode { LOGIN, SITE }

/** Site de l'enseigne dans un navigateur visible (connexion, choix du drive, vérification anti-robot). */
@Composable
expect fun RetailerWebScreen(retailer: Retailer, startUrl: String, mode: WebMode, onClose: () -> Unit)

/** Dialogue plein écran (Android : il passe sous les barres système, comme avant). */
expect fun fullScreenDialogProperties(): androidx.compose.ui.window.DialogProperties
