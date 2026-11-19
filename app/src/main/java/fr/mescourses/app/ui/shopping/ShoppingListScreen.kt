@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.mescourses.app.ui.shopping

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import fr.mescourses.app.ui.components.PageTitle
import fr.mescourses.app.ui.components.RoundIconButton
import fr.mescourses.app.ui.components.WhiteCard
import fr.mescourses.app.ui.theme.Cuisine
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import fr.mescourses.app.AppContainer
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.containerViewModel
import fr.mescourses.app.ui.components.BottomActionBar
import fr.mescourses.app.ui.components.EmptyState
import fr.mescourses.app.ui.components.SectionCard
import fr.mescourses.core.model.ItemSource
import fr.mescourses.core.model.ShoppingItem
import fr.mescourses.core.shopping.Aisle
import fr.mescourses.core.shopping.ShoppingListMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Une ligne affichée : un produit (fusion de tous les articles de même nom). */
data class ShoppingLine(
    val key: String,
    val label: String,
    val quantity: String,
    val origin: String?,
    val checked: Boolean,
    val aisle: Aisle,
    val sources: List<ShoppingItem>,
    /** Quantité unique proposée dans la feuille d'édition (null si plusieurs unités incompatibles). */
    val editQuantity: Double? = null,
    val editUnit: String? = null,
)

/** Unités proposées en un toucher dans la feuille d'édition (null = pièce). */
private val EDIT_UNITS = listOf<String?>(null, "g", "kg", "ml", "l")

class ShoppingViewModel(private val c: AppContainer) : ViewModel() {

    val lines: StateFlow<List<ShoppingLine>> = c.shopping.observeAll().map { items ->
        items.groupBy { if (ShoppingListMerger.isEgg(it.name)) "oeuf" else ShoppingListMerger.key(it.name) }.map { (key, group) ->
            val merged = ShoppingListMerger.merge(group.map { it.copy(checked = false) })
            val first = merged.first()
            ShoppingLine(
                key = key,
                label = first.name,
                quantity = merged.map { m ->
                    listOfNotNull(m.quantity?.let { ShoppingListMerger.formatNumber(it) }, m.unit).joinToString(" ")
                }.filter { it.isNotBlank() }.joinToString(" + "),
                origin = group.mapNotNull { it.origin }.distinct().joinToString(", ").ifEmpty { null },
                checked = group.all { it.checked },
                aisle = Aisle.of(first.name),
                sources = group,
                editQuantity = merged.singleOrNull()?.quantity,
                editUnit = merged.singleOrNull()?.unit,
            )
        }.sortedWith(compareBy({ it.checked }, { it.aisle.ordinal }, { it.label.lowercase() }))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(text: String) = viewModelScope.launch {
        text.split('\n', ';', ',').map { it.trim() }.filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }
            ?.let { lines -> c.shopping.add(lines.map(ShoppingListMerger::fromText)) }
    }

    fun toggle(line: ShoppingLine) = viewModelScope.launch {
        c.shopping.setChecked(line.sources.map { it.id }, !line.checked)
    }

    /**
     * Coche (ou décoche) plusieurs lignes d'un coup : toute la liste ou un rayon, en une seule requête.
     * Travaille sur les identifiants : l'état « coché » des lignes captées avant le changement n'est plus à jour.
     */
    fun setChecked(lines: List<ShoppingLine>, checked: Boolean) = viewModelScope.launch {
        c.shopping.setChecked(lines.flatMap { it.sources }.map { it.id }, checked)
    }

    fun delete(line: ShoppingLine) = viewModelScope.launch { c.shopping.deleteByIds(line.sources.map { it.id }) }

    /**
     * Modifie un article : un seul article d'origine est mis à jour ; une ligne fusionnée (plusieurs recettes)
     * est remplacée par un seul article, qui garde l'origine affichée.
     */
    fun edit(line: ShoppingLine, name: String, quantity: Double?, unit: String?) = viewModelScope.launch {
        val clean = name.trim().ifEmpty { return@launch }
        val single = line.sources.singleOrNull()
        if (single != null) {
            c.shopping.update(single.copy(name = clean, quantity = quantity, unit = unit))
        } else {
            c.shopping.deleteByIds(line.sources.map { it.id })
            c.shopping.add(
                listOf(
                    ShoppingItem(
                        name = clean, quantity = quantity, unit = unit, origin = line.origin, checked = line.checked,
                        source = if (line.sources.all { it.source == ItemSource.RECETTE }) ItemSource.RECETTE else ItemSource.MANUEL,
                    ),
                ),
            )
        }
    }

    /** Envoie les articles restants à l'écran Panier. */
    fun sendToCart() = viewModelScope.launch {
        c.pendingCart.value = fr.mescourses.app.PendingCart("Liste de courses", c.shopping.all().filterNot { it.checked })
    }

    fun restore(items: List<ShoppingItem>) = viewModelScope.launch { c.shopping.add(items) }

    /** Pop-in « Annuler » après une suppression : remet les articles (elle reste affichée même si on change d'écran). */
    fun offerRestore(message: String, removed: List<ShoppingItem>) {
        if (removed.isNotEmpty()) c.undoBar.push(message) { c.shopping.add(removed) }
    }

    /** Pop-in « Annuler » après un « tout cocher » : décoche les mêmes articles. */
    fun offerUncheck(message: String, lines: List<ShoppingLine>) {
        val ids = lines.flatMap { it.sources }.map { it.id }
        c.undoBar.push(message) { c.shopping.setChecked(ids, false) }
    }

    /** Libellés configurés du placard (clé de mémorisation pour l'UI). */
    fun currentPantry(): List<String> = c.settings.current.pantryList()

    /** Le libellé est-il un produit du placard (Réglages) ? */
    fun isPantry(name: String): Boolean = fr.mescourses.core.shopping.Pantry.isStaple(name, c.settings.current.pantryList())

    /** Retire de la liste les produits du placard non pris (ajoutés avant le filtre) ; renvoie ce qui a été retiré. */
    suspend fun removePantry(): List<ShoppingItem> {
        val removed = c.shopping.all().filter { !it.checked && isPantry(it.name) }
        c.shopping.deleteByIds(removed.map { it.id })
        return removed
    }

    /** Supprime les articles pris et renvoie ce qui a été supprimé (pour « Annuler »). */
    suspend fun clearChecked(): List<ShoppingItem> {
        val removed = c.shopping.all().filter { it.checked }
        c.shopping.clearChecked()
        return removed
    }
}

@Composable
fun ShoppingListScreen(onOpenSettings: () -> Unit, onSendToCart: () -> Unit) {
    val vm = containerViewModel { ShoppingViewModel(it) }
    val lines by vm.lines.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ShoppingLine?>(null) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val open = remember(lines) { lines.filterNot { it.checked } }
    val taken = remember(lines) { lines.filter { it.checked } }
    val grouped = remember(open) { open.groupBy { it.aisle }.toSortedMap(compareBy { it.ordinal }) }
    val pantry = vm.currentPantry()
    val pantryCount = remember(open, pantry) { open.count { vm.isPantry(it.label) } }
    val done = taken.size
    val recipes = remember(lines) { lines.flatMap { l -> l.origin?.split(", ").orEmpty() }.toSet().size }

    fun undoable(message: String, removed: List<ShoppingItem>) = vm.offerRestore(message, removed)

    /** Coche les lignes données ; « Annuler » les décoche. */
    fun checkAll(target: List<ShoppingLine>, message: String) = scope.launch {
        if (target.isEmpty()) return@launch
        vm.setChecked(target, true)
        vm.offerUncheck(message, target)
    }

    fun submit() { vm.add(input); input = "" }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            PageTitle(
                "Liste de courses",
                subtitle = if (lines.isEmpty()) null else listOfNotNull(
                    "${lines.size} article${if (lines.size > 1) "s" else ""}",
                    recipes.takeIf { it > 0 }?.let { "$it recette${if (it > 1) "s" else ""}" },
                ).joinToString(" · "),
            ) {
                RoundIconButton(Icons.Filled.Settings, "Réglages", onOpenSettings)
                if (lines.isNotEmpty()) Box {
                    RoundIconButton(Icons.Filled.MoreHoriz, "Plus d'actions", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (open.isNotEmpty()) DropdownMenuItem(
                            text = { Text("Tout cocher") }, leadingIcon = { Icon(Icons.Filled.DoneAll, null) },
                            onClick = { menu = false; checkAll(open, "${open.size} article(s) coché(s)") },
                        )
                        if (pantryCount > 0) DropdownMenuItem(
                            text = { Text("Retirer les produits du placard ($pantryCount)") }, leadingIcon = { Icon(Icons.Filled.Kitchen, null) },
                            onClick = { menu = false; scope.launch { undoable("$pantryCount produit(s) du placard retiré(s)", vm.removePantry()) } },
                        )
                        if (done > 0) DropdownMenuItem(
                            text = { Text("Retirer les articles pris ($done)") }, leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                            onClick = { menu = false; scope.launch { undoable("$done article(s) retiré(s)", vm.clearChecked()) } },
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Saisie rapide fixée en bas, au-dessus du clavier.
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 3.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("Ajouter : lessive, 2 kg de pommes…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        RoundIconButton(
                            Icons.Filled.Add, "Ajouter", { submit() },
                            container = MaterialTheme.colorScheme.onSurface, tint = MaterialTheme.colorScheme.surface,
                            size = 42.dp, enabled = input.isNotBlank(),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        },
    ) { padding ->
        if (lines.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("🧺", "Ta liste est vide", "Valide un menu, ajoute une recette, ou tape un article en bas de l'écran.")
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "progress") {
                ProgressBanner(done = done, total = lines.size, onCart = if (open.isNotEmpty()) ({ vm.sendToCart(); onSendToCart() }) else null)
            }
            if (pantryCount > 0) item(key = "pantry") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Kitchen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "$pantryCount produit${if (pantryCount > 1) "s" else ""} du placard dans la liste",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { scope.launch { undoable("$pantryCount produit(s) du placard retiré(s)", vm.removePantry()) } }) { Text("Retirer") }
                }
            }
            grouped.forEach { (aisle, group) ->
                item(key = "a-${aisle.name}") {
                    AisleCard(
                        title = aisle.label,
                        dot = aisleColor(aisle.ordinal),
                        lines = group,
                        onCheckAll = { checkAll(group, "${aisle.label} : ${group.size} article(s) coché(s)") },
                        onToggle = vm::toggle,
                        onEdit = { editing = it },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (taken.isNotEmpty()) item(key = "taken") {
                AisleCard(
                    title = "Déjà pris",
                    dot = MaterialTheme.colorScheme.outlineVariant,
                    lines = taken,
                    onCheckAll = null,
                    onToggle = vm::toggle,
                    onEdit = { editing = it },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    editing?.let { line ->
        EditItemSheet(
            line = line,
            onDismiss = { editing = null },
            onSave = { name, q, u -> vm.edit(line, name, q, u); editing = null },
            onDelete = {
                vm.delete(line); editing = null
                undoable("« ${line.label} » supprimé", line.sources)
            },
        )
    }
}

private val AISLE_COLORS = listOf(Color(0xFFCDE7A6), Color(0xFFFDE7B0), Color(0xFFF1D9C9), Color(0xFFDDE7F3), Color(0xFFF6C9A0), Color(0xFFEDE3F5))
private fun aisleColor(i: Int) = AISLE_COLORS[i % AISLE_COLORS.size]

/** Bandeau tomate : anneau d'avancement (articles pris) et accès au panier drive. */
@Composable
private fun ProgressBanner(done: Int, total: Int, onCart: (() -> Unit)?) {
    val progress by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, label = "progress")
    val left = total - done
    Surface(
        onClick = { onCart?.invoke() },
        enabled = onCart != null,
        shape = RoundedCornerShape(22.dp),
        color = if (left == 0) Cuisine.Basil else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val fg = if (left == 0) Cuisine.Coffee else MaterialTheme.colorScheme.onPrimary
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                    val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(fg.copy(alpha = 0.3f), 0f, 360f, false, style = stroke)
                    drawArc(fg, -90f, 360f * progress, false, style = stroke)
                }
                Text("$done", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = fg)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (left == 0) "Tout est pris 🎉" else "$left à prendre",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg,
                )
                Text(
                    if (left == 0) "$total article${if (total > 1) "s" else ""} dans le panier"
                    else "Ou fais-toi livrer : remplir le panier drive",
                    style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.9f),
                )
            }
            if (onCart != null) Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ShoppingCart, "Remplir le panier drive", tint = Cuisine.Tomato, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Un rayon en carte blanche : pastille de couleur, nom, compteur, « tout cocher » ; puis ses articles. */
@Composable
private fun AisleCard(
    title: String,
    dot: Color,
    lines: List<ShoppingLine>,
    onCheckAll: (() -> Unit)?,
    onToggle: (ShoppingLine) -> Unit,
    onEdit: (ShoppingLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    WhiteCard(modifier) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Text("${lines.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onCheckAll != null) IconButton(onClick = onCheckAll) {
                Icon(Icons.Filled.DoneAll, "Tout cocher : $title", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Spacer(Modifier.size(width = 12.dp, height = 48.dp))
        }
        lines.forEach { line -> ShoppingRow(line, onToggle = { onToggle(line) }, onEdit = { onEdit(line) }) }
        Spacer(Modifier.height(6.dp))
    }
}

/** Feuille « Modifier l'article » : nom, quantité, unité ; Supprimer / Enregistrer. */
@Composable
private fun EditItemSheet(line: ShoppingLine, onDismiss: () -> Unit, onSave: (String, Double?, String?) -> Unit, onDelete: () -> Unit) {
    var name by remember(line.key) { mutableStateOf(line.label) }
    var quantity by remember(line.key) { mutableStateOf(line.editQuantity?.let { ShoppingListMerger.formatNumber(it) } ?: "") }
    var unit by remember(line.key) { mutableStateOf(line.editUnit) }
    val units = remember(line.key) { (EDIT_UNITS + line.editUnit).distinct() }
    val parsed = quantity.trim().replace(',', '.').toDoubleOrNull()
    val quantityOk = quantity.isBlank() || (parsed != null && parsed > 0)
    ModalBottomSheet(onDismissRequest = onDismiss, contentWindowInsets = { WindowInsets(0) }) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Modifier l'article", style = MaterialTheme.typography.titleLarge)
            if (line.sources.size > 1) {
                Text(
                    "Cette ligne regroupe ${line.sources.size} articles (${line.origin ?: "liste"}) : ils seront remplacés par un seul.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (line.quantity.isNotBlank() && line.editQuantity == null) {
                Text("Quantité actuelle : ${line.quantity}", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Produit") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = quantity, onValueChange = { quantity = it },
                label = { Text("Quantité") }, singleLine = true, isError = !quantityOk,
                placeholder = { Text("vide = sans quantité") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { u ->
                    FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u ?: "pièce") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.DeleteOutline, null)
                    Text("Supprimer", Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = { onSave(name, parsed, if (parsed == null) null else unit) },
                    enabled = name.isNotBlank() && quantityOk,
                    modifier = Modifier.weight(1f),
                ) { Text("Enregistrer") }
            }
        }
    }
}


@Composable
private fun ShoppingRow(line: ShoppingLine, onToggle: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Grande case ronde : un toucher coche, le reste de la ligne ouvre la modification.
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).then(
                    if (line.checked) Modifier.background(MaterialTheme.colorScheme.onSurface)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (line.checked) Icon(Icons.Filled.Check, "Pris", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                line.label,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (line.checked) TextDecoration.LineThrough else null,
                color = if (line.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            line.origin?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (line.quantity.isNotBlank()) {
            Text(
                line.quantity,
                style = MaterialTheme.typography.labelLarge,
                color = if (line.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
