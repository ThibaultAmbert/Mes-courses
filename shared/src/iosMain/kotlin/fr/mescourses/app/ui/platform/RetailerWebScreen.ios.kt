package fr.mescourses.app.ui.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mescourses.app.platform.IosWeb
import fr.mescourses.app.platform.NavDelegate
import fr.mescourses.app.platform.callAsync
import fr.mescourses.app.platform.evalString
import fr.mescourses.app.platform.loadUrl
import fr.mescourses.app.retail.RetailerSites
import fr.mescourses.app.retail.SessionState
import fr.mescourses.app.retail.WkRetailerBridge
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.appContainer
import fr.mescourses.app.ui.components.BusyBar
import fr.mescourses.app.ui.components.InfoBanner
import fr.mescourses.core.carrefour.CarrefourRoutes
import fr.mescourses.core.leclerc.LeclercRoutes
import fr.mescourses.core.leclerc.LeclercStore
import fr.mescourses.core.retail.Retailer
import fr.mescourses.core.urlHost
import kotlinx.cinterop.cValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreGraphics.CGRect
import platform.WebKit.WKWebView

/**
 * iOS : site de l'enseigne dans un WKWebView visible (connexion, choix du drive, vérification anti-robot), avec le
 * même magasin de cookies et le même User-Agent que le navigateur invisible du drive. Mêmes règles qu'Android :
 * fermeture automatique une fois connecté (Carrefour), correctifs de mise en page Leclerc, drive du site repéré.
 */
@Composable
actual fun RetailerWebScreen(retailer: Retailer, startUrl: String, mode: WebMode, onClose: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val retailerSession = c.session(retailer)
    val bridge = c.bridge(retailer) as WkRetailerBridge
    val isCarrefour = retailer == Retailer.CARREFOUR
    val session by retailerSession.state.collectAsStateWithLifecycle()
    val settingsState by c.settings.state.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(true) }
    var visitedLogin by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var pageHost by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var siteStore by remember { mutableStateOf<LeclercStore?>(null) }
    var captcha by remember { mutableStateOf(false) }
    var captchaReloaded by remember { mutableStateOf(false) }

    val view = remember {
        val config = IosWeb.configuration()
        if (retailer == Retailer.AUCHAN) IosWeb.observeRequests(config) { u, h -> bridge.observe(u, h) }
        WKWebView(frame = cValue<CGRect>(), configuration = config).also {
            it.customUserAgent = bridge.userAgent()
            it.allowsBackForwardNavigationGestures = true
        }
    }

    suspend fun useLeclercStore(found: LeclercStore) {
        c.leclerc.forgetContext()
        c.settings.update {
            it.copy(
                leclercStoreId = found.id, leclercNoPR = found.noPR, leclercHost = found.host, leclercPath = found.path,
                leclercStoreName = found.name.ifBlank { "Drive ${found.id}" },
            )
        }
        siteStore = null
    }

    /** Route sécurisée testée depuis la page visible (cookies de l'utilisateur) : code HTTP, 0 si échec. */
    suspend fun probeVisible(): Int = withTimeoutOrNull(10_000) {
        view.callAsync(
            "var r=await fetch('${CarrefourRoutes.SESSION_PROBE}',{credentials:'include',headers:{'accept':'application/json, text/plain, */*','x-requested-with':'XMLHttpRequest'}});" +
                "return String(r.headers.get('cf-mitigated')?-1:r.status);",
        ).getOrNull()?.toIntOrNull()
    } ?: 0

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            if (isCarrefour && mode == WebMode.LOGIN && session != SessionState.CONNECTE &&
                view.URL?.absoluteString?.startsWith(CarrefourRoutes.ORIGIN) == true
            ) {
                withTimeoutOrNull(3_000) { if (retailerSession.isLoggedInStatus(probeVisible())) retailerSession.markConnected() }
            }
            onClose()
            c.appScope.launch { retailerSession.onVisibleSiteClosed() }
        }
    }

    LaunchedEffect(session) {
        if (isCarrefour && mode == WebMode.LOGIN && visitedLogin && session == SessionState.CONNECTE) close()
    }

    fun fixLeclerc() {
        if (retailer != Retailer.LECLERC || view.URL?.absoluteString?.contains("leclercdrive.fr") != true) return
        scope.launch {
            view.evalString(LeclercRoutes.LAYOUT_FIX_JS)
            view.evalString(LeclercRoutes.CAPTCHA_FIX_JS)
            if (settingsState.leclercUaMode == "ordinateur") view.evalString(LeclercRoutes.DESKTOP_VIEWPORT_JS)
            // Anti-robot DataDome : bandeau d'aide, et un seul rechargement si le cadre reste invisible.
            delay(1_500)
            val h = view.evalString(LeclercRoutes.CAPTCHA_PROBE_JS)?.trim('"')?.toDoubleOrNull()?.toInt() ?: -1
            captcha = h >= 0
            if (h == 0 && !captchaReloaded) {
                delay(1_500)
                if (view.evalString(LeclercRoutes.CAPTCHA_PROBE_JS)?.trim('"')?.toDoubleOrNull()?.toInt() == 0) {
                    captchaReloaded = true
                    view.reload()
                }
            }
        }
    }

    val delegate = remember {
        NavDelegate(
            onStart = { url ->
                loading = true
                pageHost = url?.let(::urlHost)
                loadError = null
                if (isCarrefour && url?.startsWith(CarrefourRoutes.IAM_ORIGIN) == true) visitedLogin = true
            },
            onFinish = { url ->
                loading = false
                if (retailer == Retailer.LECLERC) fixLeclerc() else captcha = false
                if (isCarrefour && mode == WebMode.LOGIN && visitedLogin && url?.startsWith(CarrefourRoutes.ORIGIN) == true) {
                    scope.launch { if (retailerSession.isLoggedInStatus(probeVisible())) retailerSession.markConnected() }
                }
                if (retailer == Retailer.LECLERC) RetailerSites.leclercStoreFromUrl(url)?.let { found ->
                    val current = c.settings.current.leclercStore()
                    when {
                        current == null -> scope.launch { useLeclercStore(found) }
                        current.id != found.id || current.host != found.host -> siteStore = found
                        else -> {
                            siteStore = null
                            if (!found.path.equals(current.path, ignoreCase = true)) scope.launch { c.settings.update { it.copy(leclercPath = found.path) } }
                        }
                    }
                }
            },
            onFail = { message -> loading = false; loadError = message },
            allow = { url, _ ->
                if (retailer == Retailer.AUCHAN && url?.startsWith("http") == true) bridge.observe(url, emptyMap())
                url == null || url.startsWith("http") || url.startsWith("about:") || url.startsWith("blob:")
            },
        )
    }
    DisposableEffect(view) {
        view.navigationDelegate = delegate
        view.UIDelegate = delegate
        view.loadUrl(startUrl)
        onDispose {
            view.stopLoading()
            view.navigationDelegate = null
            view.UIDelegate = null
        }
    }

    ScreenScaffold(
        title = if (mode == WebMode.LOGIN) "Connexion ${retailer.label}" else retailer.site,
        onBack = { if (view.canGoBack && !closing) view.goBack() else close() },
        actions = {
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("⋯") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Recharger") }, onClick = { menuOpen = false; loadError = null; view.reload() })
                    if (retailer == Retailer.LECLERC) {
                        listOf("ordinateur" to "Ordinateur (conseillé)", "mobile" to "Mobile Safari", "webview" to "Navigateur d'origine").forEach { (m, label) ->
                            DropdownMenuItem(
                                text = { Text("Navigateur : $label" + if (settingsState.leclercUaMode == m) " ✓" else "") },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        c.settings.update { it.copy(leclercUaMode = m) }
                                        view.customUserAgent = bridge.userAgent()
                                        view.reload()
                                        bridge.refreshUserAgent()
                                    }
                                },
                            )
                        }
                    }
                }
            }
            TextButton(onClick = { close() }, enabled = !closing) { Text("Terminé") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (mode == WebMode.LOGIN) {
                InfoBanner(
                    "🔐",
                    "Connecte-toi avec ton e-mail et ton mot de passe ${retailer.label} (la connexion « via Google » ou « via Apple » ne marche pas ici). " +
                        "L'application ne voit ni ne garde ton mot de passe. " +
                        when (retailer) {
                            Retailer.CARREFOUR -> "L'écran se ferme tout seul une fois connecté."
                            Retailer.LECLERC -> "Vérifie que le site est bien sur ton drive, puis touche « Terminé »."
                            Retailer.AUCHAN -> "Touche « Me connecter » en haut de la page, choisis ton drive si besoin, puis « Terminé »."
                        },
                    modifier = Modifier.padding(8.dp),
                )
            }
            if (loading || closing) BusyBar(Modifier.fillMaxWidth())
            Text(
                listOfNotNull(pageHost, loadError?.let { "⚠ $it" }).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = if (loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
            if (captcha) {
                InfoBanner(
                    "🤖",
                    "Leclerc demande une vérification anti-robot : résous-la ci-dessous (puzzle à glisser), puis continue sur le site.",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            siteStore?.let { found ->
                InfoBanner(
                    "🏪",
                    "Le site est sur le drive « ${found.name.ifBlank { found.id }} », l'app sur « ${settingsState.storeName(Retailer.LECLERC)} ».",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { useLeclercStore(found) } }) { Text("Utiliser ce drive") }
                    TextButton(onClick = { siteStore = null }) { Text("Garder celui de l'app") }
                }
            }
            UIKitView(factory = { view }, modifier = Modifier.fillMaxSize())
        }
    }
}
