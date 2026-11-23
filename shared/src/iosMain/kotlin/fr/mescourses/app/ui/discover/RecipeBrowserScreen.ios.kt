package fr.mescourses.app.ui.discover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mescourses.app.platform.IosWeb
import fr.mescourses.app.platform.NavDelegate
import fr.mescourses.app.platform.evalString
import fr.mescourses.app.platform.loadUrl
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.containerViewModel
import fr.mescourses.app.ui.components.BusyBar
import fr.mescourses.core.model.RecipeStatus
import fr.mescourses.core.recipes.RecipeSites
import kotlinx.cinterop.cValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import platform.CoreGraphics.CGRect
import platform.WebKit.WKWebView

private const val READ_JSON_LD =
    "JSON.stringify(Array.prototype.map.call(document.querySelectorAll('script[type=\"application/ld+json\"]'),function(s){return s.textContent;}))"

/** iOS : navigateur intégré (WKWebView) avec détection de recette, mêmes règles que sur Android. */
@Composable
actual fun RecipeBrowserScreen(startUrl: String, onClose: () -> Unit, onOpenRecipe: (Long) -> Unit, onSimplify: (String) -> Unit) {
    val vm = containerViewModel(key = "browser") { RecipeBrowserViewModel(it) }
    val detected by vm.detected.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val inBook by vm.favorites.byUrl.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messages.value = null } }
    LaunchedEffect(Unit) { vm.clear() }

    fun isRecipe(url: String) = RecipeSites.forUrl(url)?.isRecipeUrl(url) == true
    var lastSimplified by remember { mutableStateOf<Pair<String, Long>?>(null) }
    fun simplify(url: String) {
        val now = fr.mescourses.core.currentTimeMillis()
        if (lastSimplified?.let { it.first == url && now - it.second < 2_000 } == true) return
        lastSimplified = url to now
        onSimplify(url)
    }

    // La vue (et son historique) est gardée par le ViewModel pendant l'aperçu simplifié d'une recette.
    val view = remember {
        (vm.webState as? WKWebView) ?: WKWebView(frame = cValue<CGRect>(), configuration = IosWeb.configuration()).also {
            it.customUserAgent = IosWeb.mobileSafari
            it.allowsBackForwardNavigationGestures = true
            it.loadUrl(startUrl)
        }
    }

    fun scan() {
        val url = view.URL?.absoluteString ?: return
        scope.launch {
            val raw = view.evalString(READ_JSON_LD)
            val blocks = runCatching { Json.decodeFromString(ListSerializer(String.serializer()), raw.orEmpty()) }.getOrDefault(emptyList())
            vm.onPage(url, blocks)
            // Page de recette à l'adresse non reconnue mais qui contient une recette : version simplifiée.
            val found = vm.detected.value
            if (found != null && found.sourceUrl == url && url != startUrl && !isRecipe(url) && view.URL?.absoluteString == url) {
                if (view.canGoBack) view.goBack()
                simplify(url)
            }
        }
    }

    val delegate = remember {
        NavDelegate(
            onStart = { url -> loading = true; url?.let { currentUrl = it } },
            onFinish = { url ->
                loading = false
                url?.let { currentUrl = it }
                scan()
                scope.launch { delay(1_500); scan() }
            },
            onFail = { loading = false },
            allow = { url, main ->
                when {
                    url == null -> true
                    !(url.startsWith("http") || url.startsWith("about:") || url.startsWith("blob:")) -> false
                    // Toute recette ouverte depuis le site s'affiche en version simplifiée ; la page de départ reste.
                    main && url != startUrl && isRecipe(url) -> { simplify(url); false }
                    else -> true
                }
            },
        )
    }
    DisposableEffect(view) {
        view.navigationDelegate = delegate
        view.UIDelegate = delegate
        onDispose {
            view.navigationDelegate = null
            view.UIDelegate = null
            vm.webState = view
        }
    }

    ScreenScaffold(
        title = RecipeSites.forUrl(currentUrl)?.name ?: RecipeSites.hostOf(currentUrl) ?: "Parcourir",
        onBack = { if (view.canGoBack) view.goBack() else onClose() },
        snackbar = snackbar,
        actions = {
            if (RecipeSites.forUrl(currentUrl)?.isRecipeUrl(currentUrl) == true) {
                TextButton(onClick = { onSimplify(currentUrl) }) { Text("Simplifier") }
            }
            IconButton(onClick = { runCatching { uriHandler.openUri(currentUrl) } }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Ouvrir dans le navigateur")
            }
            TextButton(onClick = onClose) { Text("Terminé") }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (loading) BusyBar(Modifier.fillMaxWidth())
                UIKitView(factory = { view }, modifier = Modifier.fillMaxSize())
            }
            val r = detected
            if (r != null) {
                val book = r.sourceUrl?.let { inBook[RecipeSites.normalize(it)] }
                DetectedBanner(
                    recipe = r,
                    inFavorites = book?.status == RecipeStatus.FAVORI,
                    saving = saving,
                    onAdd = { vm.add(r) },
                    onOpen = { book?.let { onOpenRecipe(it.id) } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }
}
