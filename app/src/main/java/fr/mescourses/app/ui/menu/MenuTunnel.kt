@file:OptIn(ExperimentalLayoutApi::class)

package fr.mescourses.app.ui.menu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.PlainTooltip
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mescourses.app.ui.common.appContainer
import fr.mescourses.app.ui.components.DishArt
import fr.mescourses.app.ui.components.ModelLoadingCard
import fr.mescourses.app.ui.theme.Cuisine
import fr.mescourses.app.work.Validation
import fr.mescourses.core.catalog.Course
import fr.mescourses.core.model.MenuDish
import fr.mescourses.core.model.PlannedMeal
import fr.mescourses.core.recipes.RecipeSites
import fr.mescourses.core.season.Seasonality
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import coil.compose.AsyncImage
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Les trois parties d'un menu, dans l'ordre du repas. */
internal enum class MealGroup(val label: String, val missing: String, val course: Course?) {
    ENTREES("Entrées", "1 entrée à trouver", Course.ENTREE),
    PLATS("Plats", "1 plat à trouver", null),
    DESSERTS("Desserts", "1 dessert à trouver", Course.DESSERT),
}

internal fun groupOf(course: Course?) = when (course) {
    null -> MealGroup.PLATS
    Course.DESSERT -> MealGroup.DESSERTS
    else -> MealGroup.ENTREES
}

private fun MealGroup.target(t: Triple<Int, Int, Int>) = when (this) {
    MealGroup.ENTREES -> t.first
    MealGroup.PLATS -> t.second
    MealGroup.DESSERTS -> t.third
}

/** D'où vient le plat (site, livre ou chef) et sa durée. */
private fun originOf(d: MenuDish): String = listOfNotNull(
    d.sourceUrl?.let { RecipeSites.forUrl(it)?.name ?: "Site de recettes" },
    if (d.sourceUrl == null && d.recipeId != null) "Mon livre" else null,
    if (d.sourceUrl == null && d.recipeId == null) "Recette du chef" else null,
    d.totalMinutes?.let { minutes(it) },
).joinToString(" · ")

/** « Recette pour 6 pers., menu pour 2 » quand les deux ne correspondent pas : les quantités sont à vérifier. */
internal fun servingsMismatch(servings: Int?, portions: fr.mescourses.core.model.Portions, childFactor: Double): String? {
    if (servings == null || servings <= 0) return null
    val people = portions.equivalent(childFactor)
    if (kotlin.math.abs(people - servings) < 0.01) return null
    val n = if (people % 1.0 == 0.0) people.toInt().toString() else people.toString().replace('.', ',')
    return "Recette pour $servings pers., menu pour $n : vérifie les quantités"
}

/** Ligne d'alerte discrète (quantités à vérifier). */
@Composable
private fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Warning, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun minutes(m: Int) = if (m >= 60) "${m / 60} h" + (if (m % 60 > 0) " ${m % 60}" else "") else "$m min"

// ---------- Étape 2 : le chef cherche ----------

/** Sites qui tournent autour du chef : orbite extérieure, puis intérieure. */
private val SITES_OUTER = listOf("marmiton", "jow", "hellofresh", "cuisineaz")
private val SITES_INNER = listOf("750g", "jdf", "picnic")

/** Logo du site (icône publiée par le site, servie en PNG), lettres du nom en attendant ou si elle manque. */
private fun logoUrl(domain: String) = "https://www.google.com/s2/favicons?domain=$domain&sz=128"

@Composable
internal fun SearchScreen(progress: String?, modelLoading: Boolean, onCancel: () -> Unit) {
    // Horloge d'affichage : l'animation tourne même quand Android réduit ou coupe les animations
    // (économie d'énergie, accessibilité), ce qui figeait les transitions infinies.
    var time by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { now -> time = (now - start) / 1_000_000_000f }
    }
    val wave = (1f - cos(time * 2f * Math.PI.toFloat() / 2.4f)) / 2f
    val outer = (time * 360f / 14f) % 360f
    val inner = 360f - (time * 360f / 22f) % 360f
    val pulse = 1f + 0.18f * wave
    val bob = -6f * wave
    // Étapes réelles du moteur, déduites du message de progression.
    val stage = when {
        progress == null -> 0
        progress.contains("entrées et desserts", ignoreCase = true) -> 3
        progress.contains("choisit", ignoreCase = true) || progress.contains("compose", ignoreCase = true) -> 2
        else -> 1
    }
    val steps = listOf("Lecture de tes envies", "Recettes de saison sur les sites", "Le chef choisit les plats", "Entrées et desserts")
    Column(Modifier.fillMaxSize().background(Cuisine.Peach)) {
        TunnelHeader(current = 2, onClose = onCancel, onDark = true)
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(200.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.25f }.clip(CircleShape).background(Cuisine.Tomato))
            Box(Modifier.size(280.dp).border(1.dp, Color(0xFFEBC9AA), CircleShape))
            Box(Modifier.size(190.dp).border(1.dp, Color(0xFFEBC9AA), CircleShape))
            SITES_OUTER.forEachIndexed { i, id -> OrbitBadge(id, 140, outer + i * 360f / SITES_OUTER.size, 52, i, outer) }
            SITES_INNER.forEachIndexed { i, id -> OrbitBadge(id, 92, inner + i * 120f + 60f, 44, i + SITES_OUTER.size, outer) }
            Box(
                Modifier.offset { IntOffset(0, bob.dp.roundToPx()) }.size(104.dp).clip(CircleShape).background(Cuisine.Tomato),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.RestaurantMenu, "Le chef", tint = Color.White, modifier = Modifier.size(50.dp)) }
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Le chef cherche…", style = MaterialTheme.typography.headlineMedium, color = Cuisine.Coffee)
            Text(progress ?: "Marmiton, Jow, HelloFresh, 750g, Journal des Femmes, Picnic…", style = MaterialTheme.typography.bodyMedium, color = Cuisine.CoffeeMuted)
            if (modelLoading) ModelLoadingCard()
            steps.forEachIndexed { i, label -> StepLine(label, done = i < stage, active = i == stage) }
        }
    }
}

@Composable
private fun OrbitBadge(siteId: String, radius: Int, angle: Float, size: Int, order: Int, clock: Float) {
    val site = RecipeSites.byId(siteId) ?: return
    // Arrivée en cascade, puis chaque logo « respire » à son tour pendant qu'il tourne.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(order * 140L)
        appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 300f))
    }
    val beat = 1f + 0.12f * sin(Math.toRadians((clock * 4 + order * 60).toDouble())).toFloat().coerceAtLeast(0f)
    val rad = Math.toRadians(angle.toDouble())
    Box(
        Modifier
            .offset { IntOffset((cos(rad) * radius.dp.toPx()).roundToInt(), (sin(rad) * radius.dp.toPx()).roundToInt()) }
            .graphicsLayer { val sc = appear.value * beat; scaleX = sc; scaleY = sc }
            .size(size.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        var loaded by remember { mutableStateOf(false) }
        if (!loaded) Text(
            site.name.split(' ').joinToString("") { it.take(1) }.take(3),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Cuisine.Coffee,
        )
        AsyncImage(
            model = logoUrl(site.domain),
            contentDescription = site.name,
            onSuccess = { loaded = true },
            modifier = Modifier.size((size * 0.62f).dp).clip(RoundedCornerShape(6.dp)),
        )
    }
}

/** Ligne d'étape : faite (coche basilic), en cours (spinner tomate) ou à venir. */
@Composable
private fun StepLine(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            done -> Box(Modifier.size(24.dp).clip(CircleShape).background(Cuisine.Basil), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Cuisine.Coffee, modifier = Modifier.size(14.dp))
            }
            active -> CircularProgressIndicator(Modifier.size(22.dp), color = Cuisine.Tomato, strokeWidth = 2.5.dp, trackColor = Color(0xFFEBC9AA))
            else -> Box(Modifier.size(22.dp).border(2.dp, Color(0xFFEBC9AA), CircleShape))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (done || active) Cuisine.Coffee else Color(0xFF8A6A55))
    }
}

// ---------- Étape 2 : une recette par tuile ----------

@Composable
internal fun SwipeScreen(dishes: List<MenuDish>, targets: Triple<Int, Int, Int>, onOpen: (MenuDish) -> Unit, onFinish: (Set<String>) -> Unit) {
    val cards = remember { dishes.sortedBy { it.courseRank } }
    // Gardé quand on ouvre le détail d'une recette puis qu'on revient : le tri reprend où il en était.
    var index by rememberSaveable { mutableIntStateOf(0) }
    var kept by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var passed by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var history by rememberSaveable { mutableStateOf(arrayListOf<Boolean>()) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun decide(keep: Boolean) {
        if (index >= cards.size || offset.isRunning) return
        scope.launch {
            offset.animateTo(if (keep) 1600f else -1600f, tween(220))
            val title = cards[index].title
            if (keep) kept = ArrayList(kept + title) else passed = ArrayList(passed + title)
            history = ArrayList(history + keep)
            index++
            offset.snapTo(0f)
            if (index >= cards.size) onFinish(passed.toSet())
        }
    }

    fun undo() {
        if (history.isEmpty() || offset.isRunning) return
        val wasKept = history.last()
        history = ArrayList(history.dropLast(1))
        index--
        val title = cards[index].title
        if (wasKept) kept = ArrayList(kept - title) else passed = ArrayList(passed - title)
    }

    Column(Modifier.fillMaxSize()) {
        TunnelHeader(current = 2, onClose = { onFinish(passed.toSet()) }) {
            Text("${(index + 1).coerceAtMost(cards.size)} / ${cards.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MealGroup.entries.forEach { g ->
                val target = g.target(targets)
                val n = kept.count { title -> cards.firstOrNull { it.title == title }?.let { groupOf(it.course) } == g }
                val full = target > 0 && n >= target
                Box(
                    Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(50))
                        .background(if (full) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${g.label} $n/$target",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (full) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            cards.getOrNull(index)?.let { dish ->
                Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 18.dp).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                Box(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 9.dp).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surfaceContainer))
                Surface(
                    onClick = { onOpen(dish) },
                    enabled = dish.sourceUrl != null || dish.recipeId != null,
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxSize()
                        .offset { IntOffset(offset.value.roundToInt(), 0) }
                        .graphicsLayer { rotationZ = offset.value / 40f }
                        .pointerInput(index) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        offset.value > 260f -> decide(true)
                                        offset.value < -260f -> decide(false)
                                        else -> scope.launch { offset.animateTo(0f) }
                                    }
                                },
                                onDragCancel = { scope.launch { offset.animateTo(0f) } },
                            ) { change, amount ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + amount) }
                            }
                        },
                ) { SwipeCard(dish, offset.value) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(onClick = { decide(false) }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 3.dp, modifier = Modifier.size(68.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, "Je passe", Modifier.size(28.dp)) }
            }
            Surface(onClick = ::undo, enabled = history.isNotEmpty(), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Undo, "Annuler le dernier choix", Modifier.size(20.dp)) }
            }
            Surface(onClick = { decide(true) }, shape = CircleShape, color = Cuisine.Basil, shadowElevation = 3.dp, modifier = Modifier.size(68.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, "Je garde", Modifier.size(30.dp), tint = Cuisine.Coffee) }
            }
        }
        Text(
            "Glisse à droite pour garder, à gauche pour passer · touche la carte pour la recette",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun SwipeCard(dish: MenuDish, dx: Float) {
    val season = remember(dish) { Seasonality.report(listOf(dish.title) + dish.mainIngredients, java.time.LocalDate.now().month) }
    Column {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            DishArt(dish.title, dish.mainIngredients, imageUrl = dish.imageUrl, modifier = Modifier.fillMaxSize(), emojiSize = 72)
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Tag(groupOf(dish.course).label.removeSuffix("s"), Cuisine.Coffee, Color.White)
                dish.totalMinutes?.let { Tag(minutes(it), Color.White, Cuisine.Coffee) }
            }
            if (dx > 120f) Stamp("JE GARDE", Cuisine.Basil, Modifier.align(Alignment.CenterStart).padding(start = 20.dp), -12f)
            if (dx < -120f) Stamp("JE PASSE", Color.White, Modifier.align(Alignment.CenterEnd).padding(end = 20.dp), 12f)
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(dish.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(originOf(dish), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxLines = 2) {
                if (season.seasonal.isNotEmpty()) Tag("De saison : " + season.seasonal.take(2).joinToString(", "), Cuisine.BasilLight, Cuisine.Coffee)
                dish.mainIngredients.take(4).forEach { Tag(it, MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

@Composable
private fun Tag(text: String, background: Color, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Stamp(text: String, background: Color, modifier: Modifier, rotation: Float) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = Cuisine.Coffee,
        modifier = modifier.graphicsLayer { rotationZ = rotation }
            .clip(RoundedCornerShape(12.dp)).background(background)
            .border(3.dp, Cuisine.Coffee, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

// ---------- Étape 2 : récap ----------

@Composable
internal fun RecapScreen(
    dishes: List<MenuDish>,
    targets: Triple<Int, Int, Int>,
    state: MenuUiState,
    ready: Boolean,
    servings: Map<String, Int>,
    childFactor: Double,
    articles: Int,
    shared: Int,
    onBack: () -> Unit,
    onAdjust: () -> Unit,
    onShowSharing: () -> Unit,
    onPortions: (Int, Int, Int) -> Unit,
    onAnother: (Int) -> Unit,
    onInvent: (Int) -> Unit,
    onOpen: (String) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onRemove: (Int) -> Unit,
    onChoose: (Course?) -> Unit,
    onFillSlot: (Course?) -> Unit,
    onFillAll: () -> Unit,
    onAddMore: () -> Unit,
    onRestart: () -> Unit,
    onValidate: () -> Unit,
) {
    val busy = state.busy
    val groups = dishes.withIndex().groupBy { groupOf(it.value.course) }
    val target = targets.first + targets.second + targets.third
    val missing = MealGroup.entries.sumOf { g -> (g.target(targets) - groups[g].orEmpty().size).coerceAtLeast(0) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Tout recommencer ?") },
            text = { Text("Les plats choisis seront retirés et tu reviendras aux paramètres.") },
            confirmButton = { TextButton(onClick = { confirmRestart = false; onRestart() }) { Text("Recommencer") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Annuler") } },
        )
    }
    Column(Modifier.fillMaxSize()) {
        TunnelHeader(current = 2, onClose = onBack, back = true)
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Ta sélection", style = MaterialTheme.typography.headlineMedium)
                // Touchée : la liste des ingrédients partagés, avec « Mieux mutualiser ».
                Surface(onClick = onShowSharing, shape = RoundedCornerShape(50), color = Cuisine.BasilLight, modifier = Modifier.padding(top = 4.dp)) {
                    Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$articles articles · $shared ingrédients partagés", style = MaterialTheme.typography.labelLarge, color = Cuisine.Coffee)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Voir les ingrédients partagés", Modifier.padding(start = 4.dp).size(16.dp), tint = Cuisine.Coffee)
                    }
                }
            }
            Text("${dishes.size}", style = MaterialTheme.typography.displaySmall)
            Text("/$target", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            state.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (missing > 0) item {
                Surface(shape = RoundedCornerShape(22.dp), color = Cuisine.SaffronLight) {
                    Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (missing == 1) "1 plat manque" else "$missing plats manquent", style = MaterialTheme.typography.titleSmall, color = Cuisine.Coffee)
                            Text("Le chef les choisit d'un coup, tu peux les changer ensuite.", style = MaterialTheme.typography.bodySmall, color = Cuisine.Coffee)
                        }
                        Surface(onClick = onFillAll, enabled = !busy, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.size(6.dp))
                                Text("Tout compléter", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
            MealGroup.entries.forEach { g ->
                val list = groups[g].orEmpty()
                val goal = g.target(targets)
                val empty = (goal - list.size).coerceAtLeast(0)
                if (list.isEmpty() && empty == 0) return@forEach
                item(key = g.name) {
                    TunnelCard {
                        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp, bottom = 4.dp)) {
                                Text(g.label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text("${list.size} / $goal", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (list.size >= goal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                            }
                            list.forEach { (i, dish) ->
                                val key = "$i-${dish.title}"
                                RecapRow(
                                    dish = dish, expanded = expanded == key, enabled = !busy, ready = ready,
                                    warning = servingsMismatch(servings[dish.title], dish.portions, childFactor),
                                    onToggle = { expanded = if (expanded == key) null else key },
                                    onPortions = { a, c -> onPortions(i, a, c) },
                                    onAnother = { onAnother(i) }, onInvent = { onInvent(i) },
                                    onOpen = onOpen, onOpenRecipe = onOpenRecipe,
                                    onRemove = { expanded = null; onRemove(i) },
                                )
                            }
                            repeat(empty) {
                                EmptySlot(g.missing, enabled = !busy, onChoose = { onChoose(g.course) }, onChef = { onFillSlot(g.course) })
                            }
                        }
                    }
                }
            }
            item {
                Surface(onClick = onAddMore, enabled = !busy, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Ajouter un plat en plus", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { confirmRestart = true }, enabled = !busy) { Icon(Icons.Filled.Refresh, "Tout recommencer") }
            Surface(onClick = onAdjust, enabled = !busy, shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.height(60.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(6.dp))
                    Text("Ajuster", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.size(8.dp))
            Surface(
                onClick = onValidate, enabled = !busy && dishes.isNotEmpty(),
                shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).height(60.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(8.dp))
                    Text("Valider le menu", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun RecapRow(
    dish: MenuDish,
    expanded: Boolean,
    enabled: Boolean,
    ready: Boolean,
    warning: String?,
    onToggle: () -> Unit,
    onPortions: (Int, Int) -> Unit,
    onAnother: () -> Unit,
    onInvent: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onRemove: () -> Unit,
) {
    val container = appContainer()
    val openable = dish.sourceUrl != null || dish.recipeId != null
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            DishArt(dish.title, dish.mainIngredients, imageUrl = dish.imageUrl, emojiSize = 20, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(dish.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(originOf(dish), dish.portions.label()).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                warning?.let { WarningLine(it) }
            }
            IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Filled.Close, "Retirer ce plat", Modifier.size(18.dp)) }
        }
        if (expanded) {
            Column(Modifier.padding(start = 56.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Adultes", style = MaterialTheme.typography.labelMedium)
                    RoundStepper(dish.portions.adults, { onPortions(it - dish.portions.adults, 0) }, "Adultes", enabled = enabled, big = false, max = 20)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enfants", style = MaterialTheme.typography.labelMedium)
                    RoundStepper(dish.portions.children, { onPortions(0, it - dish.portions.children) }, "Enfants", enabled = enabled, big = false, max = 20)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (openable) RoundAction(Icons.AutoMirrored.Filled.MenuBook, "Voir la recette", enabled = true) {
                        container.hintPortions(dish.recipeId, dish.sourceUrl, dish.portions)
                        dish.sourceUrl?.let(onOpen) ?: dish.recipeId?.let(onOpenRecipe)
                    }
                    RoundAction(Icons.Filled.Autorenew, "Autre recette", enabled = enabled, onClick = onAnother)
                    RoundAction(Icons.Filled.AutoAwesome, "Le chef invente une recette à la place", enabled = enabled && ready, tint = MaterialTheme.colorScheme.primary, onClick = onInvent)
                }
            }
        }
    }
}

/** Action d'un plat en simple picto rond (le nom est lu par TalkBack et affiché à l'appui long). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    androidx.compose.material3.TooltipBox(
        positionProvider = androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = androidx.compose.material3.rememberTooltipState(),
    ) {
        Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, Modifier.size(20.dp), tint = if (enabled) tint else MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun EmptySlot(label: String, enabled: Boolean, onChoose: () -> Unit, onChef: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp, end = 8.dp)
            .border(1.5.dp, Color(0xFFD9B99C), RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Surface(onClick = onChoose, enabled = enabled, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text("Choisir", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
        }
        Surface(onClick = onChef, enabled = enabled, shape = RoundedCornerShape(50), color = Cuisine.Coffee) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp), tint = Color.White)
                Spacer(Modifier.size(4.dp))
                Text("Le chef", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

// ---------- Étape 3 : préparation de la semaine ----------

@Composable
internal fun PreparationScreen(v: Validation, dishes: List<MenuDish>, modelLoading: Boolean, onBackground: () -> Unit) {
    val frac by animateFloatAsState(v.done.toFloat() / v.total.coerceAtLeast(1), tween(600), label = "prep")
    val finished = v.done >= v.total
    val steps = listOf(
        "Portions calculées" to (v.done > 0),
        "Ingrédients extraits des recettes" to finished,
        "Liste de courses rangée par rayon" to false,
        "Recettes rangées dans ton livre" to false,
    )
    val activeIndex = steps.indexOfFirst { !it.second }
    Column(Modifier.fillMaxSize().background(Cuisine.Peach)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 76.dp, end = 16.dp, top = 30.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < 2) Cuisine.Coffee else Cuisine.Tomato)) }
        }
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { frac },
                modifier = Modifier.size(240.dp),
                color = Cuisine.Tomato,
                strokeWidth = 14.dp,
                trackColor = Color(0xFFF6D7BC),
                strokeCap = StrokeCap.Round,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${v.done}", style = MaterialTheme.typography.displayMedium, color = Cuisine.Coffee)
                    Text("/${v.total}", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF8A6A55), modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("plats préparés", style = MaterialTheme.typography.bodySmall, color = Cuisine.CoffeeMuted)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Le chef prépare ta semaine", style = MaterialTheme.typography.headlineSmall, color = Cuisine.Coffee)
            Text(
                if (v.current.isNotBlank()) "En cours : ${v.current}. Tu peux quitter, on te prévient à la fin." else "Tu peux quitter l'app, le chef continue.",
                style = MaterialTheme.typography.bodyMedium, color = Cuisine.CoffeeMuted,
            )
            if (modelLoading) ModelLoadingCard()
            steps.forEachIndexed { i, (label, done) -> StepLine(label, done = done, active = i == activeIndex) }
        }
        Surface(
            onClick = onBackground,
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp).height(60.dp)
                .border(1.dp, Color(0xFFC9A588), RoundedCornerShape(30.dp)),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("Continuer en arrière-plan", style = MaterialTheme.typography.titleSmall, color = Cuisine.Coffee) }
        }
    }
}

// ---------- Étape 3 : menu de la semaine ----------

@Composable
internal fun WeekScreen(
    week: List<PlannedMeal>,
    images: Map<Long, String>,
    servings: Map<Long, Int>,
    childFactor: Double,
    stepsProgress: Pair<Int, Int>?,
    pendingSteps: Int,
    chefInstalled: Boolean,
    onGenerate: () -> Unit,
    onStopGenerating: () -> Unit,
    onBack: () -> Unit,
    onToggle: (PlannedMeal) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenShopping: () -> Unit,
    onNewMenu: () -> Unit,
    onClear: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val shown = week.filter { m -> filter == null || groupOf(m.course).name == filter }
    val todo = shown.filterNot { it.cooked }
    val done = shown.filter { it.cooked }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Column(Modifier.weight(1f)) {
                Text("${week.count { it.cooked }} / ${week.size} plats cuisinés · dans l'ordre que tu veux", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ma semaine", style = MaterialTheme.typography.headlineMedium)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Options du menu") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Nouveau menu") }, onClick = { menuOpen = false; onNewMenu() })
                    DropdownMenuItem(text = { Text("Effacer le menu de la semaine") }, onClick = { menuOpen = false; onClear() })
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Tout", filter == null, { filter = null })
            MealGroup.entries.forEach { g -> Pill(g.label, filter == g.name, { filter = g.name }) }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Surface(onClick = onOpenShopping, shape = RoundedCornerShape(22.dp), color = Cuisine.Saffron) {
                    Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ta liste de courses est prête", style = MaterialTheme.typography.titleSmall, color = Cuisine.Coffee)
                            Text("En magasin ou au drive", style = MaterialTheme.typography.bodySmall, color = Cuisine.Coffee)
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Cuisine.Coffee)
                        }
                    }
                }
            }
            if (stepsProgress != null) item {
                val (d, total) = stepsProgress
                TunnelCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Le chef écrit les recettes : $d / $total", style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(progress = { if (total == 0) 0f else d.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
                        TextButton(onClick = onStopGenerating) { Text("Arrêter") }
                    }
                }
            } else if (pendingSteps > 0) item {
                TunnelCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$pendingSteps recette(s) sans étapes", style = MaterialTheme.typography.titleSmall)
                        Text("Le chef les écrit à partir des ingrédients, une par une, quand tu le demandes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onGenerate, enabled = chefInstalled) { Text("Générer les étapes avec le chef") }
                        if (!chefInstalled) Text("Installe l'assistant dans les Réglages pour l'utiliser.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (todo.isNotEmpty()) item { SectionLabel("À cuisiner · ${todo.size}") }
            items(todo, key = { it.id }) { meal ->
                WeekRow(meal, images[meal.recipeId], servingsMismatch(servings[meal.recipeId], meal.portions, childFactor), onToggle, onOpenRecipe)
            }
            if (done.isNotEmpty()) item { SectionLabel("Déjà cuisinés · ${done.size}") }
            items(done, key = { it.id }) { meal -> WeekRow(meal, images[meal.recipeId], null, onToggle, onOpenRecipe) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun WeekRow(meal: PlannedMeal, image: String?, warning: String?, onToggle: (PlannedMeal) -> Unit, onOpenRecipe: (Long) -> Unit) {
    val container = appContainer()
    Surface(
        onClick = { container.hintPortions(meal.recipeId, null, meal.portions); onOpenRecipe(meal.recipeId) },
        shape = RoundedCornerShape(20.dp),
        color = if (meal.cooked) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DishArt(meal.recipeTitle, imageUrl = image, emojiSize = 22, modifier = Modifier.size(if (meal.cooked) 40.dp else 56.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    meal.recipeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (meal.cooked) TextDecoration.LineThrough else null,
                    color = if (meal.cooked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (!meal.cooked) Text(
                    groupOf(meal.course).label.removeSuffix("s") + " · " + meal.portions.label(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                warning?.let { WarningLine(it) }
            }
            Surface(
                onClick = { onToggle(meal) },
                shape = CircleShape,
                color = if (meal.cooked) Cuisine.Basil else MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.size(44.dp).let { if (meal.cooked) it else it.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Check,
                        if (meal.cooked) "Remettre à cuisiner" else "Marquer comme cuisiné",
                        tint = if (meal.cooked) Cuisine.Coffee else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
