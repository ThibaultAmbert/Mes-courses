package fr.mescourses.app.ui.discover
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import fr.mescourses.app.ui.common.RecipeFiche
import fr.mescourses.app.ui.common.RecipeFicheMessage
import fr.mescourses.app.ui.components.CtaButton
import fr.mescourses.app.ui.components.Tag
import fr.mescourses.app.ui.components.WhiteCard
import fr.mescourses.app.ui.theme.Cuisine
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.ui.graphics.Color
import fr.mescourses.app.ui.components.FilterPill
import fr.mescourses.app.ui.components.RoundIconButton
import fr.mescourses.app.ui.components.SearchBox
import android.annotation.SuppressLint
import fr.mescourses.app.ui.components.BusyBar
import fr.mescourses.app.ui.components.Spinner
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.containerViewModel
import fr.mescourses.app.ui.components.BottomActionBar
import fr.mescourses.app.ui.components.DishArt
import fr.mescourses.app.ui.components.EmptyState
import fr.mescourses.app.llm.ModelStatus
import fr.mescourses.app.ui.components.InfoBanner
import fr.mescourses.core.model.Portions
import fr.mescourses.core.shopping.ShoppingListMerger
import fr.mescourses.app.ui.components.SectionCard
import fr.mescourses.app.ui.components.PillStepper
import fr.mescourses.app.ui.components.SectionHeader
import fr.mescourses.app.ui.components.StatChip
import fr.mescourses.core.model.Recipe
import fr.mescourses.core.model.RecipeStatus
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.unit.sp
import fr.mescourses.core.recipes.FilterGroup
import fr.mescourses.core.recipes.QuickIdea
import fr.mescourses.core.recipes.RecipeFilter
import fr.mescourses.core.recipes.RecipeFilters
import fr.mescourses.core.recipes.RecipeSort
import fr.mescourses.core.recipes.RecipeHit
import fr.mescourses.core.recipes.RecipeSites
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import fr.mescourses.app.ui.common.dec

// ---------------------------------------------------------------------------------------------
// Navigateur intégré avec détection de recette
// ---------------------------------------------------------------------------------------------

/** Lit le contenu des balises JSON-LD de la page (données schema.org/Recipe). */
/**
 * Réglages proches de Chrome pour les sites de recettes (Marmiton, 750g…) : sans eux, le menu ne s'ouvre pas
 * ou s'affiche cassé (fenêtre de consentement aux cookies tiers bloquée, mise en page « bureau », popups ignorées).
 */
@SuppressLint("SetJavaScriptEnabled")
private fun configureLikeChrome(wv: WebView) {
    with(wv.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        @Suppress("DEPRECATION")
        databaseEnabled = true
        mediaPlaybackRequiresUserGesture = true
        javaScriptCanOpenWindowsAutomatically = true
        // Les liens « nouvelle fenêtre » s'ouvrent dans la même vue.
        setSupportMultipleWindows(false)
        builtInZoomControls = true
        displayZoomControls = false
        userAgentString = chromeUserAgent(userAgentString)
    }
    CookieManager.getInstance().setAcceptCookie(true)
    // Bandeaux de consentement (Didomi, Sourcepoint…) servis depuis un autre domaine.
    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
    wv.webChromeClient = object : WebChromeClient() {
        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false)
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.deny()
        }
    }
}

/** User-Agent identique à Chrome pour Android (voir [RecipeSites.chromeUserAgent]). */
fun chromeUserAgent(webViewUa: String): String = RecipeSites.chromeUserAgent(webViewUa)

private const val READ_JSON_LD =
    "JSON.stringify(Array.prototype.map.call(document.querySelectorAll('script[type=\"application/ld+json\"]'),function(s){return s.textContent;}))"

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun RecipeBrowserScreen(startUrl: String, onClose: () -> Unit, onOpenRecipe: (Long) -> Unit, onSimplify: (String) -> Unit) {
    val vm = containerViewModel(key = "browser") { RecipeBrowserViewModel(it) }
    val detected by vm.detected.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val inBook by vm.favorites.byUrl.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messages.value = null } }
    LaunchedEffect(Unit) { vm.clear() }

    fun isRecipe(url: String) = RecipeSites.forUrl(url)?.isRecipeUrl(url) == true
    var lastSimplified by remember { mutableStateOf<Pair<String, Long>?>(null) }
    fun simplify(url: String) {
        // Évite d'ouvrir deux fois le même aperçu (deux événements pour une même navigation).
        val now = fr.mescourses.core.currentTimeMillis()
        if (lastSimplified?.let { it.first == url && now - it.second < 2_000 } == true) return
        lastSimplified = url to now
        onSimplify(url)
    }

    fun scan(v: WebView) {
        val url = v.url ?: return
        v.evaluateJavascript(READ_JSON_LD) { raw ->
            val blocks = runCatching {
                val text = Json.decodeFromString(String.serializer(), raw)
                Json.decodeFromString(ListSerializer(String.serializer()), text)
            }.getOrDefault(emptyList())
            vm.onPage(url, blocks)
            // Filet de sécurité : page de recette dont l'adresse n'est pas reconnue (Picnic, site qui a changé
            // ses adresses…) mais qui contient une recette → version simplifiée, en revenant à la liste.
            val found = vm.detected.value
            if (found != null && found.sourceUrl == url && url != startUrl && !isRecipe(url) && v.url == url) {
                if (v.canGoBack()) v.goBack()
                simplify(url)
            }
        }
    }

    BackHandler {
        val v = view
        if (v != null && v.canGoBack()) v.goBack() else onClose()
    }

    ScreenScaffold(
        title = RecipeSites.forUrl(currentUrl)?.name ?: RecipeSites.hostOf(currentUrl) ?: "Parcourir",
        onBack = onClose,
        snackbar = snackbar,
        actions = {
            // Page de recette mal affichée : version simplifiée lue par l'app (ingrédients, étapes).
            if (RecipeSites.forUrl(currentUrl)?.isRecipeUrl(currentUrl) == true) {
                TextButton(onClick = { onSimplify(currentUrl) }) { Text("Simplifier") }
            }
            IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Ouvrir dans le navigateur")
            }
            TextButton(onClick = onClose) { Text("Terminé") }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (loading) BusyBar(Modifier.fillMaxWidth())
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).also { wv ->
                            configureLikeChrome(wv)
                            wv.webViewClient = object : WebViewClient() {
                                // Toute recette ouverte depuis le site s'affiche en version simplifiée (aperçu de l'app).
                                // La page de départ, elle, reste visible si on a demandé explicitement le site.
                                override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (!request.isForMainFrame || url == startUrl || !isRecipe(url)) return false
                                    simplify(url)
                                    return true
                                }

                                override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                                    // Changement de page par JavaScript (location.href) vers une recette : on reste sur la liste.
                                    if (url != null && url != startUrl && isRecipe(url)) {
                                        v?.stopLoading()
                                        simplify(url)
                                        return
                                    }
                                    loading = true
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(v: WebView?, url: String?) {
                                    loading = false
                                    v ?: return
                                    scan(v)
                                    // Certains sites ajoutent la fiche recette après le chargement.
                                    scope.launch { delay(1_500); view?.let(::scan) }
                                }

                                override fun doUpdateVisitedHistory(v: WebView?, url: String?, isReload: Boolean) {
                                    // Navigation interne du site (sans rechargement) vers une recette : version simplifiée,
                                    // et le navigateur revient à la liste pour le retour arrière.
                                    if (url != null && url != startUrl && isRecipe(url)) {
                                        if (v?.canGoBack() == true) v.goBack()
                                        simplify(url)
                                        return
                                    }
                                    url?.let { currentUrl = it }
                                    v?.let { w -> scope.launch { delay(800); if (view === w) scan(w) } }
                                }
                            }
                            // Retour depuis une recette simplifiée : on restaure l'historique au lieu de repartir de la page de départ.
                            val saved = vm.webState as? android.os.Bundle
                            if (saved == null || wv.restoreState(saved) == null) wv.loadUrl(startUrl)
                            view = wv
                        }
                    },
                    onRelease = { wv ->
                        if (view === wv) view = null
                        vm.webState = android.os.Bundle().also { wv.saveState(it) }
                        wv.destroy()
                    },
                )
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

