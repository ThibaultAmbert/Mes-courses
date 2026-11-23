@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package fr.mescourses.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.mescourses.app.ui.components.BalanceDots
import fr.mescourses.app.ui.components.CtaButton
import fr.mescourses.app.ui.components.DishArt
import fr.mescourses.app.ui.components.FilterPill
import fr.mescourses.app.ui.components.PageTitle
import fr.mescourses.app.ui.components.RoundIconButton
import fr.mescourses.app.ui.components.SoftButton
import fr.mescourses.app.ui.components.Spinner
import fr.mescourses.app.ui.components.Tag
import fr.mescourses.app.ui.components.WhiteCard
import fr.mescourses.app.ui.components.balanceHint
import fr.mescourses.app.ui.menu.RoundStepper
import fr.mescourses.app.ui.theme.Cuisine
import fr.mescourses.core.menu.PlateBalance
import fr.mescourses.core.model.Portions
import fr.mescourses.core.model.Recipe
import fr.mescourses.core.shopping.ShoppingListMerger

/**
 * Fiche recette commune à toute l'appli : recette du livre (Mes recettes, menu, semaine) et aperçu d'une recette
 * en ligne (Découvrir, propositions du chef, ajout d'un plat).
 *
 * Photo en grand avec retour / favori / ⋯ par-dessus, feuille crème qui la chevauche (étiquettes, titre, portions
 * adultes et enfants, assiette), onglets Ingrédients / Étapes, et barre fixe « À la liste » + « Au menu ».
 */
@Composable
fun RecipeFiche(
    r: Recipe,
    portions: Portions,
    childFactor: Double,
    onPortions: (Portions) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    onList: () -> Unit,
    onMenu: () -> Unit,
    favorite: Boolean,
    onFavorite: () -> Unit,
    menuEnabled: Boolean = true,
    favoriteBusy: Boolean = false,
    siteName: String? = r.sourceUrl?.let { fr.mescourses.core.recipes.RecipeSites.forUrl(it)?.name },
    /** Entrées du menu ⋯ ; `close` referme le menu. */
    menuItems: @Composable (close: () -> Unit) -> Unit = {},
    /** Étiquettes propres à l'écran (cuisinée n×, exclue…), après type, durée et site. */
    extraTags: @Composable () -> Unit = {},
    /** Bandeau sous le titre (hors ligne, recette exclue…). */
    banner: @Composable ColumnScope.() -> Unit = {},
    /** Contenu de l'onglet Étapes quand la recette n'en a pas. */
    emptySteps: @Composable ColumnScope.() -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var showSteps by rememberSaveable(r.title) { mutableStateOf(false) }
    val checked = remember(r.title) { mutableStateListOf<Int>() }
    val ingredientNames = remember(r.id, r.ingredients) { r.ingredients.map { it.name } }
    val present = remember(r.id, r.title, ingredientNames) { PlateBalance.components(listOf(r.title) + ingredientNames) }
    val scaled = remember(r, portions, childFactor) { ShoppingListMerger.scale(r, portions.equivalent(childFactor)) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoftButton("À la liste", onClick = onList, icon = Icons.AutoMirrored.Filled.PlaylistAdd)
                CtaButton("Au menu", onClick = onMenu, icon = Icons.Filled.Add, enabled = menuEnabled, modifier = Modifier.weight(1f))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // 1. Photo en grand, actions rondes par-dessus.
            Box(Modifier.fillMaxWidth().height(270.dp)) {
                DishArt(r.title, ingredientNames, r.imageUrl, Modifier.fillMaxSize(), emojiSize = 96)
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Retour", onBack)
                    Spacer(Modifier.weight(1f))
                    if (favoriteBusy) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) { Spinner(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        }
                    } else RoundIconButton(
                        if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
                        onFavorite,
                        tint = Cuisine.Tomato,
                    )
                    Box {
                        RoundIconButton(Icons.Filled.MoreHoriz, "Plus d'actions", { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { menuItems { menu = false } }
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
                    siteName?.let { Tag(it) }
                    extraTags()
                }
                Text(r.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                r.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                banner()

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortionCard("Adultes", portions.adults, { onPortions(portions.copy(adults = it.coerceAtLeast(0))) }, Modifier.weight(1f))
                    PortionCard("Enfants", portions.children, { onPortions(portions.copy(children = it.coerceAtLeast(0))) }, Modifier.weight(1f))
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
                        scaled.forEachIndexed { i, ing ->
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
                                val q = listOfNotNull(ing.quantity?.let { ShoppingListMerger.formatNumber(it) }, ing.unit).joinToString(" ")
                                // Ingrédient sans quantité lisible : on garde le texte d'origine (« sel, poivre »).
                                val label = if (q.isBlank() && ing.raw.isNotBlank()) ing.raw else ing.name
                                Text(
                                    label.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (q.isNotBlank()) Text(q, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                } else {
                    if (r.steps.isEmpty()) emptySteps()
                    r.steps.forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Cuisine.SaffronLight), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = Cuisine.Coffee)
                            }
                            Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Fiche pas encore prête (chargement, erreur) : même en-tête rond, contenu centré. */
@Composable
fun RecipeFicheMessage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageTitle(title, onBack = onBack)
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
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

/** 45 → « 45 min », 150 → « 2 h 30 ». */
fun formatMinutes(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${twoDigits(m % 60)}"
}
