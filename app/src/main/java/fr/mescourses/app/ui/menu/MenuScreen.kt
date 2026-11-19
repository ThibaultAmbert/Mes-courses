@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.mescourses.app.ui.menu

import android.Manifest
import fr.mescourses.app.ui.components.BusyBar
import fr.mescourses.app.ui.components.Spinner
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mescourses.app.llm.ModelStatus
import fr.mescourses.app.ui.common.containerViewModel
import fr.mescourses.app.ui.components.DishArt
import fr.mescourses.app.ui.components.ModelLoadingCard
import fr.mescourses.app.ui.theme.Cuisine
import fr.mescourses.core.catalog.Course
import fr.mescourses.core.menu.Candidate
import fr.mescourses.core.menu.CookingTime
import fr.mescourses.core.menu.DishKind
import fr.mescourses.core.menu.Novelty
import fr.mescourses.core.model.MenuDish
import fr.mescourses.core.model.PlannedMeal

private val CHAT_SUGGESTIONS = listOf(
    "Ajoute 2 adultes au plat 1",
    "Plus rapide à préparer",
    "Plus végétarien",
    "Cherche un curry de légumes",
)

/** Ce que montre l'onglet Envie, selon l'étape et le travail du chef. */
private enum class Screen { HOME, PARAMS, SEARCH, SWIPE, RECAP, PREPARATION, WEEK }

@Composable
fun MenuScreen(onOpenSettings: () -> Unit, onOpenRecipe: (Long) -> Unit, onOpenShopping: () -> Unit, onOpenPreview: (String) -> Unit = {}) {
    val vm = containerViewModel { MenuViewModel(it) }
    val container = fr.mescourses.app.ui.common.appContainer()
    val state by vm.state.collectAsStateWithLifecycle()
    val step by vm.step.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val reviewing by vm.reviewing.collectAsStateWithLifecycle()
    val dishes by vm.dishes.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val pendingSteps by vm.pendingSteps.collectAsStateWithLifecycle()
    val stepsProgress by vm.stepsProgress.collectAsStateWithLifecycle()
    val recipeImages by vm.recipeImages.collectAsStateWithLifecycle()
    val overlap by vm.overlap.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val preview by vm.shoppingPreview.collectAsStateWithLifecycle()
    val planned by vm.planned.collectAsStateWithLifecycle()
    val draftServings by vm.draftServings.collectAsStateWithLifecycle()
    val bookServings by vm.bookServings.collectAsStateWithLifecycle()
    // Les fenêtres (ajout, discussion) ne s'affichent que sur cet écran : quand une recette est ouverte par-dessus,
    // elles disparaissent pour que le bouton Retour du téléphone revienne bien ici (et les rouvre).
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val onTop = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    var pickerQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.picker == null) { if (state.picker == null) pickerQuery = "" }
    // Demande de recette inventée : null = fermé ; -1 = plat en plus ; sinon l'index du plat à remplacer.
    var inventFor by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var chatOpen by remember { mutableStateOf(false) }
    var sharingOpen by remember { mutableStateOf(false) }
    val sharingResult by vm.sharingResult.collectAsStateWithLifecycle()
    val sharingBusy by vm.sharingBusy.collectAsStateWithLifecycle()
    val ready = state.model.available
    val askNotifications = rememberNotificationPermission()

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    // « Continuer en arrière-plan » : la préparation continue, l'écran revient à l'accueil.
    var prepHidden by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.validation == null) { if (state.validation == null) prepHidden = false }
    var lastValidation by remember { mutableStateOf(state.validation) }
    if (state.validation != null) lastValidation = state.validation

    val screen = when {
        state.validation != null && !prepHidden -> Screen.PREPARATION
        step == MenuStep.ACCUEIL -> Screen.HOME
        step == MenuStep.REGLAGES -> Screen.PARAMS
        step == MenuStep.SEMAINE -> Screen.WEEK
        searching || (dishes.isEmpty() && state.busy) -> Screen.SEARCH
        reviewing && dishes.isNotEmpty() -> Screen.SWIPE
        else -> Screen.RECAP
    }

    // Retour : arrête une recherche en cours, ou remonte vers l'accueil.
    BackHandler(enabled = screen in setOf(Screen.PARAMS, Screen.SEARCH, Screen.RECAP, Screen.WEEK)) {
        if (screen == Screen.SEARCH || (screen == Screen.RECAP && state.busy)) vm.stop() else vm.goHome()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "screen",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { target ->
            when (target) {
                Screen.HOME -> HomeScreen(
                    week = week, draft = dishes,
                    onOpenSettings = onOpenSettings,
                    onOpenWeek = { vm.goTo(MenuStep.SEMAINE) },
                    onContinueDraft = vm::continueDraft,
                    onCompleteDraft = vm::completeDraft,
                    onClearDraft = vm::clearDraft,
                    onNewMenu = vm::newMenu,
                )
                Screen.PARAMS -> ParamsScreen(
                    vm = vm, state = state,
                    onClose = vm::goHome,
                    onLaunch = { askNotifications(); vm.propose() },
                )
                Screen.SEARCH -> SearchScreen(progress = state.progress, modelLoading = state.model == ModelStatus.Loading, onCancel = vm::stop)
                Screen.SWIPE -> SwipeScreen(
                    dishes = dishes, targets = vm.targets(),
                    onOpen = { d ->
                        container.hintPortions(d.recipeId, d.sourceUrl, d.portions)
                        d.sourceUrl?.let(onOpenPreview) ?: d.recipeId?.let(onOpenRecipe)
                    },
                    onFinish = { passed -> vm.finishReview(passed) },
                )
                Screen.RECAP -> RecapScreen(
                    dishes = dishes, targets = remember(planned, dishes) { vm.targets() }, state = state, ready = ready,
                    servings = draftServings, childFactor = vm.childFactor(),
                    articles = preview?.items ?: insights.articles, shared = overlap.shared.size,
                    onBack = vm::goHome,
                    onAdjust = { chatOpen = true },
                    onShowSharing = { vm.sharingResult.value = null; sharingOpen = true },
                    onPortions = vm::changePortions,
                    onAnother = vm::anotherRecipe,
                    onInvent = { inventFor = it },
                    onOpen = onOpenPreview, onOpenRecipe = onOpenRecipe,
                    onRemove = vm::removeDish,
                    onChoose = vm::setPickerCourse,
                    onFillSlot = vm::fillSlot,
                    onFillAll = vm::fillMissing,
                    onAddMore = vm::openPicker,
                    onRestart = vm::restart,
                    onValidate = { askNotifications(); vm.validate() },
                )
                Screen.PREPARATION -> (state.validation ?: lastValidation)?.let { v ->
                    PreparationScreen(v, dishes, modelLoading = state.model == ModelStatus.Loading, onBackground = { prepHidden = true; vm.goHome() })
                }
                Screen.WEEK -> WeekScreen(
                    week = week, images = recipeImages, servings = bookServings, childFactor = vm.childFactor(),
                    stepsProgress = stepsProgress, pendingSteps = pendingSteps,
                    chefInstalled = state.model != ModelStatus.Absent,
                    onGenerate = vm::generateMissingSteps, onStopGenerating = vm::stopGeneratingSteps,
                    onBack = vm::goHome,
                    onToggle = vm::toggleCooked, onOpenRecipe = onOpenRecipe,
                    onOpenShopping = onOpenShopping, onNewMenu = vm::newMenu, onClear = vm::clearWeek,
                )
            }
        }
    }

    inventFor?.let { target ->
        InventDialog(
            replacing = dishes.getOrNull(target)?.title,
            onDismiss = { inventFor = null },
            onCreate = { idea -> inventFor = null; vm.invent(target.takeIf { it >= 0 }, idea) },
        )
    }

    if (sharingOpen && onTop) {
        SharingSheet(
            dishes = dishes, report = overlap, insights = insights, preview = preview,
            busy = state.busy || sharingBusy, canUndo = state.canUndo, result = sharingResult,
            canImprove = overlap.isolated.size > 1,
            onImprove = vm::improveSharing, onUndo = { vm.undo(); vm.sharingResult.value = "Dernier changement annulé." },
            onDismiss = { sharingOpen = false },
        )
    }

    if (chatOpen && onTop) {
        ChatSheet(
            chat = state.chat, busy = state.busy, ready = ready, modelLoading = state.model == ModelStatus.Loading,
            onSend = vm::send, onStop = vm::stop, onImprove = vm::improveSharing,
            canUndo = state.canUndo, onUndo = { vm.undo() }, onAddDish = { chatOpen = false; vm.openPicker() },
            onDismiss = { chatOpen = false },
        )
    }

    state.picker?.takeIf { onTop }?.let { recipes ->
        AddDishSheet(
            recipes = recipes, busy = state.pickerBusy, course = state.pickerCourse, canInvent = ready,
            query = pickerQuery, onQuery = { pickerQuery = it },
            onCourse = vm::setPickerCourse,
            onSearch = vm::searchPicker, onPick = { vm.addCandidate(it) }, onOpen = onOpenPreview,
            onInvent = { idea -> vm.closePicker(); vm.invent(null, idea) },
            onDismiss = vm::closePicker,
        )
    }
}

/**
 * Android 13+ : demande (une fois) le droit d'afficher la notification « le chef travaille », qui permet
 * de continuer quand on quitte l'app. Sans ce droit, le travail continue quand même, sans notification visible.
 */
@Composable
private fun rememberNotificationPermission(): () -> Unit {
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

// ---------- Éléments communs du tunnel ----------

/** En-tête du tunnel : fermer / retour, barre d'étapes (1 à 3) et compteur. */
@Composable
internal fun TunnelHeader(current: Int, onClose: () -> Unit, back: Boolean = false, onDark: Boolean = false, trailing: @Composable () -> Unit = {}) {
    val ink = if (onDark) Cuisine.Coffee else MaterialTheme.colorScheme.onSurface
    val btnBg = if (onDark) Color.White else MaterialTheme.colorScheme.surfaceContainerLowest
    val trackOn = if (onDark) Cuisine.Coffee else MaterialTheme.colorScheme.primary
    val trackOff = if (onDark) Color(0xFFEBC9AA) else MaterialTheme.colorScheme.outlineVariant
    val countColor = if (onDark) Cuisine.CoffeeMuted else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(onClick = onClose, shape = CircleShape, color = btnBg, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (back) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close,
                    contentDescription = if (back) "Retour" else "Fermer",
                    tint = ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i < current) trackOn else trackOff),
                )
            }
        }
        Text("$current/3", style = MaterialTheme.typography.labelLarge, color = countColor)
        trailing()
    }
}

/** Grand bouton d'action du tunnel : pilule tomate avec une pastille flèche. */
@Composable
internal fun PrimaryCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(30.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth().height(60.dp),
    ) {
        Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.size(44.dp).clip(CircleShape).background(Cuisine.Saffron), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Cuisine.Coffee)
            }
        }
    }
}

/** Compteur rond « − valeur + ». */
@Composable
internal fun RoundStepper(value: Int, onChange: (Int) -> Unit, label: String, min: Int = 0, max: Int = 21, enabled: Boolean = true, big: Boolean = true) {
    val size = if (big) 40.dp else 32.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = { onChange(value - 1) }, enabled = enabled && value > min, shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.size(size),
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Remove, "$label : moins", Modifier.size(18.dp)) } }
        Text(
            "$value",
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            modifier = Modifier.widthIn(min = 28.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Surface(
            onClick = { onChange(value + 1) }, enabled = enabled && value < max, shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.size(size),
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "$label : plus", Modifier.size(18.dp)) } }
    }
}

/** Choix unique en segments (temps en cuisine, classiques / découvertes). */
@Composable
internal fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, enabled: Boolean, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Surface(
                onClick = { onSelect(o) }, enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                color = if (on) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label(o),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Pastille sélectionnable (envies, filtres). */
@Composable
internal fun Pill(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = if (selected) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/** Carte blanche arrondie du tunnel. */
@Composable
internal fun TunnelCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = modifier.fillMaxWidth()) {
        content()
    }
}

// ---------- Accueil de l'onglet Envie ----------

@Composable
private fun HomeScreen(
    week: List<PlannedMeal>,
    draft: List<MenuDish>,
    onOpenSettings: () -> Unit,
    onOpenWeek: () -> Unit,
    onContinueDraft: () -> Unit,
    onCompleteDraft: () -> Unit,
    onClearDraft: () -> Unit,
    onNewMenu: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                "On prévoit quoi ?",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, letterSpacing = (-0.5).sp),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Surface(onClick = onOpenSettings, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Settings, "Paramètres", Modifier.size(22.dp)) }
            }
        }

        // Menu de la semaine : seulement s'il existe.
        if (week.isNotEmpty()) {
            val cooked = week.count { it.cooked }
            Surface(onClick = onOpenWeek, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Cuisine.BasilLight), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, tint = Cuisine.Coffee)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Menu de la semaine", style = MaterialTheme.typography.titleSmall)
                        Text("Validé · $cooked / ${week.size} plats cuisinés", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Menu commencé (plats envoyés depuis Recettes, ou proposition non validée) : seulement s'il existe.
        if (draft.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 2.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Menu commencé",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Cuisine.Coffee,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Cuisine.SaffronLight).padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onClearDraft) { Text("Vider") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 104.dp, height = 52.dp)) {
                            draft.take(3).forEachIndexed { i, d ->
                                DishArt(
                                    d.title, d.mainIngredients, imageUrl = d.imageUrl, emojiSize = 20,
                                    modifier = Modifier.offset(x = (i * 26).dp).size(52.dp).clip(RoundedCornerShape(14.dp))
                                        .border(3.dp, MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(14.dp)),
                                )
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("${draft.size} recette${if (draft.size > 1) "s" else ""} déjà choisie${if (draft.size > 1) "s" else ""}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                draft.take(3).joinToString(", ") { it.title } + if (draft.size > 3) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = onContinueDraft, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).height(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("Continuer ce menu", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary) }
                        }
                        Surface(onClick = onCompleteDraft, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.height(48.dp)) {
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Compléter", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(onClick = onNewMenu, shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).fillMaxSize()) {
                Box {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = (-36).dp).size(130.dp).clip(CircleShape).background(Color(0xFFE2603F)))
                    Box(Modifier.align(Alignment.TopEnd).offset(x = (-22).dp, y = 62.dp).size(44.dp).clip(CircleShape).background(Cuisine.Saffron))
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("LA SEMAINE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Color.White)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Prévoir un menu", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            Text("Le chef propose, tu choisis.", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }
            }
            // Tunnel apéro / soirée : pas encore défini, la tuile n'a pas d'action.
            Surface(shape = RoundedCornerShape(26.dp), color = Cuisine.Saffron, contentColor = Cuisine.Coffee, modifier = Modifier.weight(1f).fillMaxSize()) {
                Box {
                    Row(Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White))
                        Box(Modifier.size(22.dp).clip(CircleShape).background(Cuisine.Basil))
                    }
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("UN MOMENT", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Cuisine.Coffee)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Apéro ou soirée", style = MaterialTheme.typography.headlineSmall, color = Cuisine.Coffee)
                            Text("Bientôt", style = MaterialTheme.typography.bodySmall, color = Cuisine.Coffee)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Étape 1 : paramètres ----------

@Composable
private fun ParamsScreen(vm: MenuViewModel, state: MenuUiState, onClose: () -> Unit, onLaunch: () -> Unit) {
    val enabled = !state.busy
    var more by rememberSaveable { mutableStateOf(false) }
    val total = state.starters + state.meals + state.desserts
    Column(Modifier.fillMaxSize()) {
        TunnelHeader(current = 1, onClose = onClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Ta semaine", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))

            TunnelCard {
                Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                    listOf(
                        Triple("Adultes", state.adults, vm::setAdults),
                        Triple("Enfants", state.children, vm::setChildren),
                    ).forEach { (label, value, set) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            RoundStepper(value, set, label, min = if (label == "Adultes") 1 else 0, max = 20, enabled = enabled)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Entrées", state.starters, vm::setStarters) to 0,
                    Triple("Plats", state.meals, vm::setMeals) to 1,
                    Triple("Desserts", state.desserts, vm::setDesserts) to 0,
                ).forEach { (t, min) ->
                    val (label, value, set) = t
                    TunnelCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$value", style = MaterialTheme.typography.displaySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(onClick = { set(value - 1) }, enabled = enabled && value > min, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.size(36.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Remove, "$label : moins", Modifier.size(18.dp)) }
                                }
                                Surface(onClick = { set(value + 1) }, enabled = enabled && value < 21, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.size(36.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "$label : plus", Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }

            TunnelCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Temps en cuisine · ${state.time.detail}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Segmented(CookingTime.entries, state.time, { it.label }, enabled, vm::setTime)
                    Text("Recettes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Segmented(Novelty.entries, state.novelty, { if (it == Novelty.CLASSIQUES) "Classiques" else it.label }, enabled, vm::setNovelty)
                    Text("Envies", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QUICK_WISHES.forEach { w ->
                            Pill(w, state.wishes.split(',').any { it.trim().equals(w, true) }, { vm.toggleWish(w) }, enabled)
                        }
                    }
                }
            }

            TunnelCard {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { more = !more }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Affiner", style = MaterialTheme.typography.titleSmall)
                            Text("Végé / viande / poisson, à écouler, une envie précise", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (more) "Replier" else "Déplier")
                    }
                    if (more) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple("Végé", state.vegetarian, DishKind.VEGE),
                                Triple("Viande", state.meat, DishKind.VIANDE),
                                Triple("Poisson", state.fish, DishKind.POISSON),
                            ).forEach { (label, value, kind) ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    RoundStepper(value, { vm.setQuota(kind, it) }, "Repas $label", enabled = enabled, big = false)
                                }
                            }
                        }
                        val free = state.meals - state.vegetarian - state.fish - state.meat
                        Text(
                            when {
                                free == state.meals -> "Rien d'imposé : je varie pour toi."
                                free > 0 -> "$free repas libre${if (free > 1) "s" else ""}."
                                else -> "Tous les repas sont répartis."
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("À écouler", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.useUp.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.useUp.forEach { item ->
                                    InputChip(
                                        selected = false, onClick = { vm.removeUseUp(item) }, enabled = enabled,
                                        label = { Text(item) },
                                        trailingIcon = { Icon(Icons.Filled.Close, "Retirer $item", Modifier.size(16.dp)) },
                                    )
                                }
                            }
                        }
                        var input by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = input, onValueChange = { input = it },
                            placeholder = { Text("ex. un demi chou, crème fraîche") },
                            singleLine = true, enabled = enabled, shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.addUseUp(input); input = "" }),
                            trailingIcon = {
                                IconButton(onClick = { vm.addUseUp(input); input = "" }, enabled = enabled && input.isNotBlank()) { Icon(Icons.Filled.Add, "Ajouter") }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Une envie précise", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = state.wishes, onValueChange = vm::setWishes,
                            placeholder = { Text("ex. « une raclette samedi »") },
                            enabled = enabled, maxLines = 3, shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "De saison : " + (vm.season.vegetables.take(8) + vm.season.fruits.take(4)).joinToString(", ") + "…",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            ChefLine(state.model, onRetry = vm::loadModel)
        }
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)) {
            PrimaryCta("Lancer le chef · $total plat${if (total > 1) "s" else ""}", onLaunch, enabled = enabled)
        }
    }
}

/** État de l'assistant, en une ligne discrète (il n'est pas nécessaire pour composer un menu). */
@Composable
private fun ChefLine(model: ModelStatus, onRetry: () -> Unit) {
    when (model) {
        is ModelStatus.Sleeping -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("Le chef (${model.modelName}) est en veille.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Activer") }
        }
        ModelStatus.Loading -> ModelLoadingCard()
        is ModelStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("Le chef n'a pas pu démarrer : ${model.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Réessayer") }
        }
        else -> Unit
    }
}

// ---------- Fenêtres ----------

/** Demande d'une recette créée par le chef (LLM), avec une idée facultative. */
@Composable
private fun InventDialog(replacing: String?, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var idea by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer une recette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (replacing != null) "Le chef invente une recette à la place de « $replacing »." else "Le chef invente une recette et l'ajoute au menu.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = idea,
                    onValueChange = { idea = it },
                    placeholder = { Text("Une idée ? ex. « curry de légumes d'hiver »") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Laisse vide : il choisit un plat de saison qui réutilise les ingrédients du menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(idea) }) { Text("Créer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ChatSheet(
    chat: List<ChatLine>,
    busy: Boolean,
    ready: Boolean,
    modelLoading: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onImprove: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onAddDish: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastLenBucket = (chat.lastOrNull()?.text?.length ?: 0) / 40
    LaunchedEffect(chat.size, lastLenBucket, busy) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
    }
    // Le clavier est géré ici seulement (pas aussi par la feuille), et la liste rétrécit pour laisser voir le champ.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, contentWindowInsets = { WindowInsets(0) }) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Ajuster avec le chef", style = MaterialTheme.typography.titleLarge)
            Text(
                "Une demande à la fois : invités sur un plat, ajouter, retirer ou remplacer un plat…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (modelLoading) ModelLoadingCard(Modifier.padding(top = 8.dp))
            else if (busy) BusyBar(Modifier.fillMaxWidth().padding(top = 8.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 72.dp, max = 320.dp).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (chat.isEmpty()) item {
                    Text("Pas encore de message.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                itemsIndexed(chat, key = { i, _ -> i }) { _, line -> ChatBubble(line) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canUndo) SuggestionChip(onClick = onUndo, enabled = !busy, label = { Text("Annuler la retouche") })
                SuggestionChip(onClick = onAddDish, enabled = !busy, label = { Text("Ajouter un plat") })
                SuggestionChip(onClick = onImprove, enabled = ready && !busy, label = { Text("Mieux mutualiser") })
                CHAT_SUGGESTIONS.forEach { s ->
                    SuggestionChip(onClick = { input = s }, enabled = !busy, label = { Text(s) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("« ajoute 3 adultes et 2 enfants au plat 2 »") },
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 3,
                    enabled = !busy,
                )
                Spacer(Modifier.size(8.dp))
                if (busy) {
                    FilledIconButton(onClick = onStop) { Icon(Icons.Filled.Stop, "Arrêter") }
                } else {
                    FilledIconButton(onClick = { onSend(input); input = "" }, enabled = input.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Envoyer")
                    }
                }
            }
        }
    }
}

/**
 * « Ajouter un plat » : recettes disponibles, recherche (catalogue ou sites) et, en bas, création par le chef
 * à partir du texte tapé. Une seule fenêtre pour ajouter ou faire inventer un plat.
 */
@Composable
private fun AddDishSheet(
    recipes: List<Candidate>,
    busy: Boolean,
    course: Course?,
    canInvent: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onCourse: (Course?) -> Unit,
    onSearch: (String) -> Unit,
    onPick: (Candidate) -> Unit,
    onOpen: (String) -> Unit,
    onInvent: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, contentWindowInsets = { WindowInsets(0) }) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ajouter un plat", style = MaterialTheme.typography.titleLarge)
            Segmented(
                listOf<Course?>(Course.ENTREE, null, Course.DESSERT), course,
                { when (it) { Course.ENTREE -> "Entrée"; Course.DESSERT -> "Dessert"; else -> "Plat" } },
                enabled = true, onSelect = onCourse,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Une idée, un plat, un ingrédient…") },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                trailingIcon = { TextButton(onClick = { onSearch(query) }, enabled = !busy) { Text("Chercher") } },
            )
            if (busy) BusyBar(Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 72.dp, max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (recipes.isEmpty() && !busy) item {
                    Text("Aucune recette disponible : lance une recherche ci-dessus.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                items(recipes, key = { it.recipe.sourceUrl ?: it.recipe.title }) { c ->
                    Card(onClick = { onPick(c) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.recipe.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        c.kind.label, c.minutes?.let { "$it min" }, c.siteName ?: if (c.fromCookbook) "Mon livre" else null,
                                        c.seasonal.takeIf { it.isNotEmpty() }?.let { "de saison : " + it.take(2).joinToString(", ") },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            c.recipe.sourceUrl?.let { url -> TextButton(onClick = { onOpen(url) }) { Text("Voir") } }
                        }
                    }
                }
            }
            // Création par le chef, à partir de ce qui est tapé.
            Surface(
                onClick = { onInvent(query) }, enabled = canInvent,
                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Cuisine.Saffron), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Cuisine.Coffee)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            if (query.isBlank()) "Le chef invente un plat" else "Créer « ${query.trim()} » avec le chef",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            if (canInvent) "Recette inventée sur ton téléphone" else "Installe l'assistant dans les Réglages pour l'utiliser",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChatBubble(line: ChatLine) {
    if (line.tool) {
        Text(
            line.text,
            style = MaterialTheme.typography.labelMedium,
            color = if (line.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            maxLines = 3,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
        Text(
            line.text.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (line.fromUser) 20.dp else 4.dp,
                        bottomEnd = if (line.fromUser) 4.dp else 20.dp,
                    ),
                )
                .background(if (line.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * Liste de courses du menu en cours : ce que les plats partagent (et quels plats), ce qui ne sert qu'à un plat,
 * ce qui restera. « Mieux mutualiser » remplace un plat par une recette qui réutilise davantage les mêmes ingrédients ;
 * le résultat s'affiche ici, et le changement peut être annulé.
 */
@Composable
private fun SharingSheet(
    dishes: List<MenuDish>,
    report: fr.mescourses.core.menu.IngredientOverlap.Report,
    insights: fr.mescourses.core.menu.MenuInsights,
    preview: fr.mescourses.core.menu.ShoppingPreview?,
    busy: Boolean,
    canUndo: Boolean,
    result: String?,
    canImprove: Boolean,
    onImprove: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showIsolated by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, contentWindowInsets = { WindowInsets(0) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ta liste de courses", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "${preview?.items ?: insights.articles}" to "articles",
                    "${report.shared.size}" to "partagés",
                    "${insights.seasonal.size}" to "de saison",
                ).forEachIndexed { i, (n, label) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (i == 1) Cuisine.BasilLight else MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(n, style = MaterialTheme.typography.titleLarge, color = if (i == 1) Cuisine.Coffee else MaterialTheme.colorScheme.onSurface)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (i == 1) Cuisine.Coffee else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "PARTAGÉS ENTRE PLUSIEURS PLATS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (report.shared.isEmpty()) item {
                    Text("Aucun ingrédient ne sert à plusieurs plats pour l'instant.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(report.shared, key = { it.name }) { u ->
                    val used = u.dishes.mapNotNull { dishes.getOrNull(it - 1) }
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    used.joinToString(" · ") { it.title },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(Modifier.size(width = (28 + 18 * (used.size.coerceAtMost(3) - 1)).dp, height = 28.dp)) {
                                used.take(3).forEachIndexed { i, d ->
                                    DishArt(
                                        d.title, d.mainIngredients, imageUrl = d.imageUrl, emojiSize = 12,
                                        modifier = Modifier.offset(x = (i * 18).dp).size(28.dp).clip(CircleShape)
                                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
                                    )
                                }
                            }
                            Text("×${u.dishes.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (report.isolated.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showIsolated = !showIsolated }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "POUR UN SEUL PLAT · ${report.isolated.size}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                            )
                            Icon(if (showIsolated) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (showIsolated) "Replier" else "Déplier")
                        }
                        if (showIsolated) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            report.isolated.forEach { u ->
                                Text(
                                    u.name, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
                if (!preview?.leftovers.isNullOrEmpty()) item {
                    Text("Il restera : " + preview?.leftovers.orEmpty().joinToString(", ") + ".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (insights.outOfSeason.isNotEmpty()) item {
                    Text(
                        "Hors saison : " + insights.outOfSeason.joinToString(", ") { (n, name) -> "$name (${dishes.getOrNull(n - 1)?.title ?: "plat $n"})" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            result?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = Cuisine.Coffee,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cuisine.SaffronLight).padding(12.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canUndo) TextButton(onClick = onUndo, enabled = !busy) { Text("Annuler") }
                Surface(
                    onClick = onImprove, enabled = canImprove && !busy,
                    shape = RoundedCornerShape(30.dp),
                    color = if (canImprove && !busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (busy) Spinner(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = if (canImprove) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Mieux mutualiser", style = MaterialTheme.typography.titleSmall,
                            color = if (canImprove && !busy) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Le chef remplace un plat par une recette qui réutilise davantage les mêmes ingrédients. Entrées et desserts restent en place.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
