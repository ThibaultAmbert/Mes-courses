@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package fr.mescourses.app.ui.cookbook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import fr.mescourses.app.ui.common.recipeCourse
import fr.mescourses.app.ui.components.CtaButton
import fr.mescourses.app.ui.components.FilterPill
import fr.mescourses.app.ui.components.NavRow
import fr.mescourses.app.ui.components.PageTitle
import fr.mescourses.app.ui.components.RoundIconButton
import fr.mescourses.app.ui.components.SearchBox
import fr.mescourses.app.ui.components.SoftButton
import fr.mescourses.app.ui.components.Tag
import fr.mescourses.app.ui.components.WhiteCard
import fr.mescourses.app.ui.menu.RoundStepper
import fr.mescourses.app.ui.theme.Cuisine
import fr.mescourses.app.ui.discover.DiscoverViewModel
import fr.mescourses.app.ui.components.Spinner
import androidx.compose.runtime.saveable.rememberSaveable
import fr.mescourses.app.llm.ModelStatus
import fr.mescourses.app.ui.components.ChefCard
import fr.mescourses.app.ui.components.ModelLoadingCard
import fr.mescourses.core.menu.MenuValidator
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.containerViewModel
import fr.mescourses.app.ui.components.BalanceDots
import fr.mescourses.app.ui.components.BottomActionBar
import fr.mescourses.app.ui.components.DishArt
import fr.mescourses.app.ui.components.EmptyState
import fr.mescourses.app.ui.components.PillStepper
import fr.mescourses.app.ui.components.SectionCard
import fr.mescourses.app.ui.components.SectionHeader
import fr.mescourses.app.ui.components.StatChip
import fr.mescourses.app.ui.discover.AddByLinkDialog
import fr.mescourses.app.ui.discover.DiscoverContent
import fr.mescourses.app.ui.components.balanceHint
import fr.mescourses.core.menu.PlateBalance
import fr.mescourses.core.model.Recipe
import fr.mescourses.core.model.RecipeStatus
import fr.mescourses.core.shopping.ShoppingListMerger

private val RecipeStatus.emoji get() = when (this) {
    RecipeStatus.HISTORIQUE -> "🕘"
    RecipeStatus.FAVORI -> "⭐"
    RecipeStatus.BANNI -> "🚫"
}

@Composable
fun CookbookScreen(
    importUrl: String?,
    onImportConsumed: () -> Unit,
    onImported: (Long) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onNewRecipe: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPreview: (String) -> Unit,
    onBrowse: (String) -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val vm = containerViewModel { CookbookViewModel(it) }
    val discover by vm.discover.collectAsStateWithLifecycle()
    // Même instance que celle de l'écran Découvrir (même écran, même clé).
    val discoverVm = containerViewModel { DiscoverViewModel(it) }
    val discoverState by discoverVm.state.collectAsStateWithLifecycle()
    val discoverPopular by discoverVm.popularMode.collectAsStateWithLifecycle()
    // Retour depuis Découvrir : résultats → accueil de Découvrir → bibliothèque.
    BackHandler(enabled = discover) {
        if (discoverState == DiscoverViewModel.State.Idle && !discoverPopular) vm.discover.value = false else discoverVm.back()
    }
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var addSheet by remember { mutableStateOf(false) }

    // Un lien partagé n'est importé qu'une fois, même si l'écran est recréé (retour, rotation).
    var importedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(importUrl) {
        if (importUrl != null && importedUrl != importUrl) {
            importedUrl = importUrl
            onImportConsumed()
            vm.import(importUrl) { onImported(it) }
        }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messages.value = null } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (discover) {
                PageTitle("Découvrir", onBack = {
                    if (discoverState == DiscoverViewModel.State.Idle && !discoverPopular) vm.discover.value = false else discoverVm.back()
                })
            } else {
                PageTitle(
                    "Mes recettes",
                    subtitle = counts.values.sum().let { if (it == 1) "1 recette" else "$it recettes" },
                ) {
                    RoundIconButton(Icons.Filled.Settings, "Réglages", onOpenSettings)
                    RoundIconButton(
                        Icons.Filled.Add, "Ajouter une recette", { addSheet = true },
                        container = MaterialTheme.colorScheme.primary, tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (discover) {
                DiscoverContent(snackbar = snackbar, onOpenPreview = onOpenPreview, onBrowse = onBrowse, onOpenCatalog = onOpenCatalog)
                return@Column
            }
            SearchBox(query, { vm.query.value = it }, "Chercher dans mes recettes", Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CookbookViewModel.TAB_ORDER.forEach { s ->
                    FilterPill(s.shortLabel, selected = tab == s, onClick = { vm.tab.value = s }, count = counts[s] ?: 0, leading = s.icon)
                }
            }
            if (importing) Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Import de la recette…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(2) }, key = "discover") {
                    DiscoverEntry(onClick = { vm.discover.value = true })
                }
                if (tab == RecipeStatus.BANNI && recipes.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            "Le chef ne proposera jamais ces plats. Garde un plat appuyé pour le déplacer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                if (recipes.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        when {
                            query.isNotBlank() -> EmptyState("🔎", "Aucune recette ne correspond", "Essaie un autre mot, ou cherche dans « Découvrir ».")
                            tab == RecipeStatus.HISTORIQUE -> EmptyState("🕘", "Ton historique est vide", "Les plats des menus que tu valides arrivent ici.")
                            tab == RecipeStatus.FAVORI -> EmptyState(
                                "⭐", "Aucun favori",
                                "Cherche-en dans « Découvrir », partage une recette depuis ton navigateur, colle un lien, ou écris-la. Le chef pourra te la reproposer.",
                            )
                            else -> EmptyState("🚫", "Aucun plat exclu", "Les plats déplacés ici ne te seront plus jamais proposés.")
                        }
                    }
                }
                items(recipes, key = { it.id }) { r ->
                    RecipeCard(r, onClick = { onOpenRecipe(r.id) }, onMove = { vm.move(r, it) }, modifier = Modifier.animateItem())
                }
            }
        }
    }

    if (addSheet) {
        AddRecipeSheet(
            onDismiss = { addSheet = false },
            onImport = { url -> addSheet = false; vm.import(url) { onOpenRecipe(it) } },
            onSearchSites = { addSheet = false; vm.discover.value = true },
            onWrite = { addSheet = false; onNewRecipe() },
        )
    }
}

private val RecipeStatus.shortLabel get() = when (this) {
    RecipeStatus.FAVORI -> "Favoris"
    RecipeStatus.HISTORIQUE -> "Historique"
    RecipeStatus.BANNI -> "Exclus"
}

private val RecipeStatus.icon get() = when (this) {
    RecipeStatus.FAVORI -> Icons.Filled.Star
    RecipeStatus.HISTORIQUE -> Icons.Filled.History
    RecipeStatus.BANNI -> Icons.Filled.Block
}

/** Carte jaune « Découvrir de nouvelles recettes ». */
@Composable
private fun DiscoverEntry(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = Cuisine.Saffron, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Découvrir de nouvelles recettes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Cuisine.Coffee)
                Text("Marmiton, Jow, 750g… sans quitter l'appli", style = MaterialTheme.typography.bodySmall, color = Cuisine.Coffee)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Cuisine.Coffee)
            }
        }
    }
}

/** Feuille « Ajouter une recette » : coller un lien, chercher sur les sites, écrire. */
@Composable
private fun AddRecipeSheet(onDismiss: () -> Unit, onImport: (String) -> Unit, onSearchSites: () -> Unit, onWrite: () -> Unit) {
    var link by remember { mutableStateOf("") }
    val clean = fr.mescourses.core.recipes.RecipeSites.findUrl(link) ?: link.trim()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, contentWindowInsets = { WindowInsets(0) }) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Ajouter une recette", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = link,
                    onValueChange = { link = it },
                    leadingIcon = { Icon(Icons.Filled.Link, null) },
                    placeholder = { Text("Coller un lien…") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (clean.startsWith("http")) onImport(clean) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                CtaButton("Importer", onClick = { onImport(clean) }, enabled = clean.startsWith("http"))
            }
            Spacer(Modifier.height(6.dp))
            NavRow(Icons.Filled.Public, Cuisine.BasilLight, "Chercher sur les sites", "Marmiton, Jow, HelloFresh, 750g…", onSearchSites)
            NavRow(Icons.Filled.Edit, Cuisine.SaffronLight, "Écrire une recette", "Ingrédients et étapes, à la main", onWrite)
            Text(
                "Astuce : depuis ton navigateur, « Partager » une page recette vers Mes courses l'importe directement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun RecipeCard(r: Recipe, onClick: () -> Unit, onMove: (RecipeStatus) -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = { menu = true }),
    ) {
        Column {
            Box {
                DishArt(r.title, r.ingredients.map { it.name }, r.imageUrl, Modifier.fillMaxWidth().height(108.dp), emojiSize = 44)
                if (r.status != RecipeStatus.HISTORIQUE) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (r.banned) Icons.Filled.Block else Icons.Filled.Star, null,
                            tint = Cuisine.Tomato, modifier = Modifier.size(17.dp),
                        )
                    }
                }
                recipeCourse(r).takeIf { it != fr.mescourses.core.catalog.Course.AUTRE }?.let { course ->
                    Text(
                        course.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Cuisine.Coffee,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Box(Modifier.align(Alignment.BottomEnd)) {
                    MoveMenu(expanded = menu, current = r.status, onDismiss = { menu = false }) { menu = false; onMove(it) }
                }
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val minutes = ((r.prepMinutes ?: 0) + (r.cookMinutes ?: 0)).takeIf { it > 0 }
                val site = r.sourceUrl?.let { fr.mescourses.core.recipes.RecipeSites.forUrl(it)?.name }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (minutes != null) Icon(Icons.Filled.Schedule, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOfNotNull(
                            minutes?.let { formatMinutes(it) },
                            r.timesCooked.takeIf { it > 0 }?.let { "cuisiné ${it}×" },
                            site,
                        ).joinToString(" · ").ifEmpty { "${r.servings} pers." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 45 → « 45 min », 150 → « 2 h 30 ». */
internal fun formatMinutes(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${"%02d".format(m % 60)}"
}

/** Menu « Déplacer vers… » une autre liste du livre. */
@Composable
fun MoveMenu(expanded: Boolean, current: RecipeStatus, onDismiss: () -> Unit, onMove: (RecipeStatus) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        RecipeStatus.entries.filter { it != current }.forEach { s ->
            DropdownMenuItem(text = { Text("${s.emoji}  Déplacer vers « ${s.label} »") }, onClick = { onMove(s) })
        }
    }
}

@Composable
fun RecipeDetailScreen(recipeId: Long, onBack: () -> Unit, onEdit: () -> Unit, onSendToCart: () -> Unit) {
    val vm = containerViewModel(key = "recipe-$recipeId") { RecipeDetailViewModel(it, recipeId) }
    val recipe by vm.recipe.collectAsStateWithLifecycle()
    val portions by vm.portions.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val stepsState by vm.steps.collectAsStateWithLifecycle()
    val modelStatus by vm.model.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showSteps by rememberSaveable { mutableStateOf(false) }
    val checked = remember { mutableStateListOf<Int>() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messages.value = null } }

    val r = recipe
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (r != null) Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoftButton("À la liste", onClick = { vm.addToList(r) }, icon = Icons.AutoMirrored.Filled.PlaylistAdd)
                CtaButton("Au menu", onClick = { vm.addToMenu(r) }, icon = Icons.Filled.Add, enabled = !r.banned, modifier = Modifier.weight(1f))
            }
        },
    ) { padding ->
        if (r == null) return@Scaffold
        val ingredientNames = remember(r.id, r.ingredients) { r.ingredients.map { it.name } }
        val present = remember(r.id, r.title, ingredientNames) { PlateBalance.components(listOf(r.title) + ingredientNames) }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // 1. Photo en grand, actions rondes par-dessus.
            Box(Modifier.fillMaxWidth().height(270.dp)) {
                DishArt(r.title, ingredientNames, r.imageUrl, Modifier.fillMaxSize(), emojiSize = 96)
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Retour", onBack)
                    Spacer(Modifier.weight(1f))
                    RoundIconButton(
                        if (r.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (r.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                        { vm.move(r, if (r.favorite) RecipeStatus.HISTORIQUE else RecipeStatus.FAVORI) },
                        tint = Cuisine.Tomato,
                    )
                    Box {
                        RoundIconButton(Icons.Filled.MoreHoriz, "Plus d'actions", { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Modifier") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(
                                text = { Text("Envoyer au panier drive") }, leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                                onClick = { menu = false; vm.sendToCart(r); onSendToCart() },
                            )
                            if (r.banned) DropdownMenuItem(
                                text = { Text("Proposer de nouveau") }, leadingIcon = { Icon(Icons.Filled.Restore, null) },
                                onClick = { menu = false; vm.move(r, RecipeStatus.HISTORIQUE) },
                            ) else DropdownMenuItem(
                                text = { Text("Ne plus proposer") }, leadingIcon = { Icon(Icons.Filled.Block, null) },
                                onClick = { menu = false; vm.move(r, RecipeStatus.BANNI) },
                            )
                            r.sourceUrl?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("Voir la recette d'origine") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                    onClick = { menu = false; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
            // 2. Feuille crème qui chevauche la photo.
            Column(
                Modifier.offset(y = (-28).dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(start = 20.dp, end = 20.dp, top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recipeCourse(r).takeIf { it != fr.mescourses.core.catalog.Course.AUTRE }?.let { Tag(it.label, Cuisine.SaffronLight, color = Cuisine.Coffee) }
                    val total = (r.prepMinutes ?: 0) + (r.cookMinutes ?: 0)
                    if (total > 0) Tag(formatMinutes(total), icon = Icons.Filled.Schedule)
                    r.sourceUrl?.let { fr.mescourses.core.recipes.RecipeSites.forUrl(it)?.name }?.let { Tag(it) }
                    if (r.timesCooked > 0) Tag("Cuisinée ${r.timesCooked}×", Cuisine.BasilLight, color = Cuisine.Coffee)
                    if (r.banned) Tag("Exclue", MaterialTheme.colorScheme.errorContainer, icon = Icons.Filled.Block, color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Text(r.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                r.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortionCard("Adultes", portions.adults, { vm.setPortions(portions.copy(adults = it.coerceAtLeast(0))) }, Modifier.weight(1f))
                    PortionCard("Enfants", portions.children, { vm.setPortions(portions.copy(children = it.coerceAtLeast(0))) }, Modifier.weight(1f))
                }
                if (r.servings != portions.adults + portions.children) {
                    Text(
                        "Recette prévue pour ${r.servings} : les quantités sont adaptées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val hint = balanceHint(present)
                if (hint != null || r.notes != null) WhiteCard(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Assiette", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        BalanceDots(present)
                    }
                    hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    r.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterPill("Ingrédients", selected = !showSteps, onClick = { showSteps = false }, count = r.ingredients.size)
                    FilterPill("Étapes", selected = showSteps, onClick = { showSteps = true }, count = r.steps.size)
                }
                if (!showSteps) {
                    Column {
                        ShoppingListMerger.scale(r, portions.equivalent(vm.childFactor())).forEachIndexed { i, ing ->
                            val done = i in checked
                            Row(
                                Modifier.fillMaxWidth().clickable { if (done) checked.remove(i) else checked.add(i) }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape).then(
                                        if (done) Modifier.background(MaterialTheme.colorScheme.onSurface)
                                        else Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) { if (done) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(14.dp)) }
                                Text(
                                    ing.name.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                val q = listOfNotNull(ing.quantity?.let { ShoppingListMerger.formatNumber(it) }, ing.unit).joinToString(" ")
                                if (q.isNotBlank()) Text(q, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                } else {
                    if (r.steps.isEmpty()) StepsPlaceholder(r, stepsState, modelStatus, onWrite = vm::writeSteps)
                    r.steps.forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(Cuisine.SaffronLight),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = Cuisine.Coffee) }
                            Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Supprimer cette recette ?") },
        text = { Text("Pour que le chef ne la propose plus, mieux vaut la passer en « Ne plus proposer ».") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
    )
}

/** Compteur de convives dans une carte blanche : « Adultes  − 2 + ». */
@Composable
private fun PortionCard(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = modifier) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
            RoundStepper(value, onChange, label, big = false)
        }
    }
}

/** Étapes absentes : récupération sur le site, écriture par le chef, ou bouton pour la demander. */
@Composable
private fun StepsPlaceholder(r: Recipe, stepsState: RecipeDetailViewModel.StepsState, modelStatus: ModelStatus, onWrite: () -> Unit) {
    when (val st = stepsState) {
        RecipeDetailViewModel.StepsState.Fetching ->
            ChefCard("Recherche des étapes sur le site…", "Récupération de la recette d'origine.", emoji = "🌐")
        RecipeDetailViewModel.StepsState.Writing ->
            if (modelStatus == ModelStatus.Loading) ModelLoadingCard()
            else ChefCard("Le chef écrit la recette…", "Étapes et temps de cuisson, à partir des ingrédients.")
        is RecipeDetailViewModel.StepsState.Queued -> ChefCard(
            "En attente du chef…",
            if (st.position <= 1) "C'est la prochaine recette qu'il écrit." else "${st.position - 1} recette(s) avant celle-ci.",
            emoji = "⏳",
        )
        is RecipeDetailViewModel.StepsState.Failed -> Column {
            Text(st.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onWrite) { Text("Réessayer") }
        }
        RecipeDetailViewModel.StepsState.Idle -> if (MenuValidator.STEPS_PENDING in r.tags) {
            WhiteCard(padding = PaddingValues(16.dp)) {
                Text(
                    "Les étapes n'ont pas pu être lues sur le site. Le chef peut les écrire à partir des ingrédients.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                CtaButton("Écrire les étapes avec le chef", onClick = onWrite, icon = Icons.Filled.AutoAwesome, enabled = modelStatus != ModelStatus.Absent)
                if (modelStatus == ModelStatus.Absent) {
                    Text("Installe le chef dans les Réglages pour l'utiliser.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            Text("Pas d'étapes pour cette recette.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RecipeEditScreen(recipeId: Long, onDone: () -> Unit) {
    val vm = containerViewModel(key = "edit-$recipeId") { RecipeEditViewModel(it, recipeId) }
    val title by vm.title.collectAsStateWithLifecycle()
    val servings by vm.servings.collectAsStateWithLifecycle()
    val ingredients by vm.ingredients.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(16.dp)
    ScreenScaffold(
        title = if (recipeId == 0L) "Nouvelle recette" else "Modifier la recette",
        onBack = onDone,
        bottomBar = {
            BottomActionBar {
                TextButton(onClick = onDone) { Text("Annuler") }
                Button(onClick = { vm.save(onDone) }, enabled = title.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DishArt(title.ifBlank { "Nouvelle recette" }, modifier = Modifier.fillMaxWidth().height(120.dp).clip(MaterialTheme.shapes.extraLarge))
            SectionCard {
                OutlinedTextField(title, { vm.title.value = it }, label = { Text("Titre") }, shape = shape, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    servings, { vm.servings.value = it.filter(Char::isDigit) }, label = { Text("Pour combien de personnes ?") }, shape = shape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
            SectionCard {
                Text("🧺 Ingrédients", style = MaterialTheme.typography.titleSmall)
                Text("Un par ligne, avec la quantité : « 200 g de farine », « 2 œufs ».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(ingredients, { vm.ingredients.value = it }, minLines = 6, shape = shape, modifier = Modifier.fillMaxWidth())
            }
            SectionCard {
                Text("👩‍🍳 Étapes", style = MaterialTheme.typography.titleSmall)
                Text("Une étape par ligne.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(steps, { vm.steps.value = it }, minLines = 6, shape = shape, modifier = Modifier.fillMaxWidth())
            }
            SectionCard {
                OutlinedTextField(tags, { vm.tags.value = it }, label = { Text("Tags (séparés par des virgules)") }, shape = shape, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

