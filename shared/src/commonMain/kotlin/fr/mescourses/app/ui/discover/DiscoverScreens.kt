@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

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
import fr.mescourses.app.ui.components.BusyBar
import fr.mescourses.app.ui.components.Spinner
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
// Onglet « Découvrir »
// ---------------------------------------------------------------------------------------------

/** Contenu de l'onglet « Découvrir » du livre de recettes. */
@Composable
fun DiscoverContent(
    snackbar: SnackbarHostState,
    onOpenPreview: (String) -> Unit,
    onBrowse: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = containerViewModel { DiscoverViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val sites by vm.sites.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val popular by vm.popularMode.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    val inBook by vm.favorites.byUrl.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val sort by vm.sort.collectAsStateWithLifecycle()
    val localOnly by vm.localOnly.collectAsStateWithLifecycle()
    val searchingOnline by vm.searchingOnline.collectAsStateWithLifecycle()
    val reloading by vm.reloading.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messages.value = null } }

    fun submit() { focus.clearFocus(); vm.search() }

    // Tirer l'écran vers le bas relance la recherche : une nouvelle liste de recettes.
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = reloading,
        onRefresh = { vm.reload() },
        modifier = modifier.fillMaxSize(),
    ) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. Recherche + bouton Filtres (avec le nombre de filtres actifs).
                val active = filters.size + (if (sites.isNotEmpty()) 1 else 0) + (if (sort != RecipeSort.PERTINENCE) 1 else 0)
                SearchBox(
                    query, { vm.query.value = it }, "Un plat, un ingrédient…",
                    onSearch = { submit() },
                    trailing = {
                        RoundIconButton(
                            Icons.Filled.Tune, "Filtres", { focus.clearFocus(); showFilters = true },
                            container = MaterialTheme.colorScheme.surfaceContainerLow, size = 38.dp, badge = active,
                        )
                    },
                )
                // 2. Accès rapide au type de plat : Tout · Entrée · Plat · Dessert (un seul à la fois).
                CourseSwitch(filters, onPick = { f -> focus.clearFocus(); vm.toggleFilter(f) })
                // 3. Populaires, autres types (apéritif, soupe, brunch) et filtres actifs (supprimables).
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item {
                        FilterPill("Populaires", selected = popular, onClick = { focus.clearFocus(); vm.togglePopular() }, leading = Icons.Filled.LocalFireDepartment)
                    }
                    items(RecipeFilters.tiles.filterNot { it in QUICK_COURSES }, key = { "t-" + it.name }) { f ->
                        FilterPill("${f.emoji} ${f.label}", selected = f in filters, onClick = { focus.clearFocus(); vm.toggleFilter(f) })
                    }
                    items(filters.sortedBy { it.ordinal }.filterNot { it in RecipeFilters.tiles }, key = { it.name }) { f ->
                        FilterPill("${f.emoji} ${f.label}", selected = true, onClick = { vm.toggleFilter(f) }, leading = Icons.Filled.Close)
                    }
                    if (filters.isNotEmpty()) item { TextButton(onClick = { vm.clearFilters() }) { Text("Tout effacer") } }
                }
            }
        }

        when (val s = state) {
            DiscoverViewModel.State.Idle -> {
                item(span = { GridItemSpan(2) }) {
                    IdleDiscover(
                        ideas = vm.seasonIdeas,
                        quickIdeas = vm.quickIdeas,
                        onIdea = { focus.clearFocus(); vm.searchIdea(it) },
                        onQuickIdea = { focus.clearFocus(); vm.applyIdea(it) },
                        onBrowse = onBrowse,
                    )
                }
            }
            DiscoverViewModel.State.Loading -> {
                // Pendant un rechargement, l'indicateur du geste suffit.
                if (!reloading) item(span = { GridItemSpan(2) }) {
                    Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spinner(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            when {
                                popular -> "Recettes populaires du moment…"
                                else -> "Recherche…"
                            },
                            Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            is DiscoverViewModel.State.Done -> {
                val r = s.result
                item(span = { GridItemSpan(2) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val answered = r.hits.map { it.siteName }.distinct().size
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (r.hits.isEmpty()) "Aucune recette trouvée"
                            else (if (popular) "🔥 Populaires en ce moment · " else "") +
                                "${r.hits.size} recettes · $answered site${if (answered > 1) "s" else ""}" +
                                (if (localOnly) " · catalogue" else "") +
                                if (sort != RecipeSort.PERTINENCE) " · ${sort.label.lowercase()}" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.reload() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Text("Nouvelle liste", Modifier.padding(start = 4.dp))
                        }
                        }
                        // Résultats du catalogue local : la recherche en ligne reste possible, en complément.
                        if (searchingOnline) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spinner(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Recherche sur les sites…", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (localOnly) {
                            OutlinedButton(onClick = { vm.searchOnline() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                Text("Chercher aussi sur les sites")
                            }
                        }
                        // Site muet ou illisible : on propose d'ouvrir sa recherche dans le navigateur de l'app.
                        (r.failures.keys + r.empty).forEach { site ->
                            Text(
                                "${site.name} : ${if (site in r.failures) "pas de réponse" else "aucun résultat lisible"} — ouvrir sur le site ›",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onBrowse(vm.searchUrl(site)) }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                if (r.hits.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        EmptyState("🔎", "Rien trouvé", "Essaie un autre mot (un ingrédient, un plat), ou retire un filtre.")
                    }
                }
                items(r.hits, key = { it.url }) { hit ->
                    val book = inBook[RecipeSites.normalize(hit.url)]
                    HitCard(
                        hit = hit,
                        badge = RecipeFilters.badge(hit.title),
                        favorite = book?.status == RecipeStatus.FAVORI,
                        adding = hit.url in adding,
                        onClick = { onOpenPreview(hit.url) },
                        onStar = { vm.addFavorite(hit) },
                    )
                }
            }
        }
    }
    }

    if (showFilters) FiltersSheet(vm, onDismiss = { showFilters = false }, onOpenCatalog = onOpenCatalog)
}

/** Types de plat en accès direct au-dessus des résultats. */
private val QUICK_COURSES = listOf(RecipeFilter.ENTREE, RecipeFilter.PLAT, RecipeFilter.DESSERT)

/**
 * Bascule segmentée « Tout · Entrée · Plat · Dessert ». Le type de plat est un filtre à choix unique :
 * choisir un segment remplace le type actif, « Tout » le retire.
 */
@Composable
private fun CourseSwitch(filters: Set<RecipeFilter>, onPick: (RecipeFilter) -> Unit) {
    val currentType = filters.firstOrNull { it.group == FilterGroup.TYPE }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        (listOf<RecipeFilter?>(null) + QUICK_COURSES).forEach { f ->
            val on = if (f == null) currentType == null else f == currentType
            Surface(
                onClick = {
                    when {
                        f == null -> currentType?.let(onPick)
                        !on -> onPick(f)
                        else -> onPick(f) // 2ᵉ toucher : retire le type
                    }
                },
                shape = RoundedCornerShape(50),
                color = if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (f == null) "Tout" else "${f.emoji} ${f.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Feuille « Filtres » : groupes de filtres, sites et tri ; « Voir les recettes » lance la recherche. */
@Composable
private fun FiltersSheet(vm: DiscoverViewModel, onDismiss: () -> Unit, onOpenCatalog: () -> Unit) {
    val filters by vm.filters.collectAsStateWithLifecycle()
    val sites by vm.sites.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val counts by vm.catalogCounts.collectAsStateWithLifecycle()
    val catalog by vm.catalogStatus.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtres", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (filters.isNotEmpty()) TextButton(onClick = vm::clearFiltersQuietly) { Text("Tout effacer") }
            }
            FilterGroup.entries.forEach { group ->
                Text(group.label + if (group.single) " (un seul)" else "", style = MaterialTheme.typography.titleSmall)
                val inGroup = RecipeFilter.entries.filter { it.group == group }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    inGroup.forEach { f ->
                        FilterChip(selected = f in filters, onClick = { vm.toggleFilterQuietly(f) }, label = { Text("${f.emoji} ${f.label}") })
                    }
                }
                inGroup.filter { it in filters && it.note != null }.forEach { f ->
                    Text("${f.label} : ${f.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Trier par", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RecipeSort.entries.forEach { s -> FilterChip(selected = sort == s, onClick = { vm.setSort(s) }, label = { Text(s.label) }) }
            }
            Text("Sites", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FilterChip(selected = sites.isEmpty(), onClick = { vm.allSites() }, label = { Text("Tous") })
                vm.allSitesList.forEach { site ->
                    val n = counts[site.id]
                    // Base de ce site pas encore téléchargée : puce grisée, non sélectionnable ; un toucher ouvre l'écran de choix.
                    if (catalog.loaded && vm.hasCatalog(site.id) && site.id !in catalog.installed) {
                        FilterChip(
                            selected = false,
                            onClick = { onDismiss(); onOpenCatalog() },
                            label = { Text("${site.name} ⬇") },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            ),
                            modifier = Modifier.alpha(0.7f),
                        )
                    } else FilterChip(
                        selected = site.id in sites,
                        onClick = { vm.toggleSite(site.id) },
                        label = { Text(if (n != null && n > 0) "${site.name} · $n" else site.name) },
                    )
                }
            }
            Text(
                "Le nombre indique les recettes déjà dans le catalogue du téléphone. Un site grisé n'est pas téléchargé : touche-le pour choisir les bases à télécharger.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { vm.refresh(); onDismiss() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Voir les recettes") }
        }
    }
}

@Composable
private fun IdleDiscover(
    ideas: List<String>,
    quickIdeas: List<QuickIdea>,
    onIdea: (String) -> Unit,
    onQuickIdea: (QuickIdea) -> Unit,
    onBrowse: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        SectionHeader("Idées rapides", emoji = "✨")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            quickIdeas.forEach { idea ->
                AssistChip(onClick = { onQuickIdea(idea) }, label = { Text("${idea.emoji} ${idea.label}") })
            }
        }
        if (ideas.isNotEmpty()) {
            SectionHeader("De saison en ce moment", emoji = "🍂")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ideas.forEach { idea ->
                    FilterChip(
                        selected = false,
                        onClick = { onIdea(idea) },
                        label = { Text(idea.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    )
                }
            }
        }
    }
}

@Composable
private fun HitCard(hit: RecipeHit, badge: RecipeFilter?, favorite: Boolean, adding: Boolean, onClick: () -> Unit, onStar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box {
            DishArt(hit.title, imageUrl = hit.imageUrl, modifier = Modifier.fillMaxWidth().height(110.dp), emojiSize = 40)
            Text(
                hit.siteName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            // Pastille de catégorie reconnue dans le titre (Poisson, Dessert…).
            badge?.let { b ->
                Text(
                    "${b.emoji} ${b.label}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp).clip(CircleShape).clickable(onClick = onStar),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (adding) Spinner(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(
                        if (favorite) "★" else "☆",
                        color = if (favorite) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(hit.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                hit.totalMinutes?.let { "$it min" },
                hit.rating?.let { r -> "★ ${dec(r, 1)}" + (hit.ratingCount?.let { " ($it)" } ?: "") },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Aperçu d'une recette
// ---------------------------------------------------------------------------------------------

@Composable
fun RecipePreviewScreen(url: String, onBack: () -> Unit, onOpenRecipe: (Long) -> Unit, onBrowse: (String) -> Unit) {
    val vm = containerViewModel(key = "preview-$url") { RecipePreviewViewModel(it, url) }
    val state by vm.state.collectAsStateWithLifecycle()
    val inBook by vm.favorites.byUrl.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val portions by vm.portions.collectAsStateWithLifecycle()
    val chefStatus by vm.chefStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messages.value = null } }
    val book = inBook[RecipeSites.normalize(url)]
    val siteName = vm.site?.name ?: RecipeSites.hostOf(url) ?: "le site"

    when (val s = state) {
        RecipePreviewViewModel.State.Loading -> RecipeFicheMessage("Recette", onBack) {
            Spinner(Modifier.size(40.dp))
            Text("Lecture de la recette sur $siteName…", Modifier.padding(top = 12.dp))
        }
        is RecipePreviewViewModel.State.Failed -> RecipeFicheMessage("Recette", onBack) {
            EmptyState("😕", "Recette illisible", s.message, actionLabel = "Ouvrir la page dans l'app", onAction = { onBrowse(url) })
            TextButton(onClick = { vm.load() }) { Text("Réessayer") }
        }
        is RecipePreviewViewModel.State.Loaded -> {
            val r = s.recipe
            val favorite = book?.status == RecipeStatus.FAVORI
            RecipeFiche(
                r = r,
                portions = portions,
                childFactor = vm.childFactor(),
                onPortions = vm::setPortions,
                onBack = onBack,
                snackbar = snackbar,
                onList = { vm.addToList(r) },
                onMenu = { vm.addToMenu(r) },
                menuEnabled = book?.status != RecipeStatus.BANNI,
                siteName = siteName,
                // Déjà en favori : l'étoile ouvre la recette du livre (modifiable) ; sinon elle l'y ajoute.
                favorite = favorite,
                onFavorite = { if (favorite && book != null) onOpenRecipe(book.id) else vm.addFavorite(r) },
                menuItems = { close ->
                    if (book != null) DropdownMenuItem(
                        text = { Text("Ouvrir dans Mes recettes") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                        onClick = { close(); onOpenRecipe(book.id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Voir sur $siteName") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                        onClick = { close(); onBrowse(url) },
                    )
                },
                extraTags = {
                    if (favorite) Tag("Dans tes favoris", Cuisine.BasilLight, icon = Icons.Filled.Star, color = Cuisine.Coffee)
                    if (book?.status == RecipeStatus.BANNI) Tag("Exclue", MaterialTheme.colorScheme.errorContainer, color = MaterialTheme.colorScheme.onErrorContainer)
                },
                banner = {
                    if (offline) InfoBanner("📴", "Hors ligne : informations déjà connues (ingrédients, temps). Les étapes seront lues sur le site dès que tu auras internet.")
                },
                emptySteps = {
                    val chefInstalled = chefStatus != ModelStatus.Absent
                    WhiteCard(padding = PaddingValues(16.dp)) {
                        Text(
                            if (chefInstalled) "Pas d'étapes lisibles sur $siteName. Le chef peut les écrire : la recette est d'abord ajoutée à tes favoris."
                            else "Pas d'étapes lisibles sur $siteName. Installe le chef dans les Réglages pour qu'il les écrive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        CtaButton("Écrire les étapes avec le chef", onClick = { vm.generateSteps(r, onOpenRecipe) }, icon = Icons.Filled.AutoAwesome, enabled = chefInstalled)
                        TextButton(onClick = { onBrowse(url) }) { Text("Voir sur $siteName") }
                    }
                },
            )
        }
    }
}

// Navigateur intégré avec détection de recette : vue native (WebView Android, WKWebView iOS).
@Composable
expect fun RecipeBrowserScreen(startUrl: String, onClose: () -> Unit, onOpenRecipe: (Long) -> Unit, onSimplify: (String) -> Unit = {})

@Composable
internal fun DetectedBanner(recipe: Recipe, inFavorites: Boolean, saving: Boolean, onAdd: () -> Unit, onOpen: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DishArt(recipe.title, recipe.ingredients.map { it.name }, recipe.imageUrl, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), emojiSize = 24)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "✓ Recette détectée · ${recipe.ingredients.size} ingrédients",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            when {
                saving -> Spinner(Modifier.size(24.dp), strokeWidth = 2.dp)
                inFavorites -> OutlinedButton(onClick = onOpen) { Text("Ouvrir") }
                else -> Button(onClick = onAdd) { Text("⭐ Ajouter") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Ajout par lien
// ---------------------------------------------------------------------------------------------

/** Lien http(s) actuellement dans le presse-papiers, s'il y en a un. */
@Suppress("DEPRECATION")
private fun clipboardUrl(clipboard: androidx.compose.ui.platform.ClipboardManager): String? =
    runCatching { RecipeSites.findUrl(clipboard.getText()?.text) }.getOrNull()

@Composable
fun AddByLinkDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    @Suppress("DEPRECATION") val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var url by remember { mutableStateOf("") }
    val copied = remember { clipboardUrl(clipboard) }
    val clean = RecipeSites.findUrl(url) ?: url.trim()
    val site = RecipeSites.forUrl(clean)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Link, null) },
        title = { Text("Ajouter une recette par lien") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (copied != null && copied != clean) {
                    InfoBanner(
                        "📋",
                        "Lien copié : ${copied.removePrefix("https://").removePrefix("www.").take(48)}… — appuie pour l'utiliser",
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { url = copied },
                        color = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("Adresse de la page") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (clean.startsWith("http")) onImport(clean) }),
                )
                if (clean.startsWith("http")) {
                    Text(
                        if (site != null) "✓ Site compatible (${site.name})" else "Site non reconnu : l'app essaiera quand même de lire la recette.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (site != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (site != null) FontWeight.Bold else null,
                    )
                }
                Text(
                    "Astuce : depuis Chrome, « Partager » → Mes courses fait la même chose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(enabled = clean.startsWith("http"), onClick = { onImport(clean) }) { Text("Importer dans les favoris") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
