package fr.mescourses.app.ui.cart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import fr.mescourses.app.ui.components.CtaButton
import fr.mescourses.app.ui.components.GroupLabel
import fr.mescourses.app.ui.components.SoftButton
import fr.mescourses.app.ui.components.WhiteCard
import fr.mescourses.app.ui.theme.Cuisine
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import fr.mescourses.app.AppContainer
import fr.mescourses.app.PendingQuote
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.containerViewModel
import fr.mescourses.app.ui.components.InfoBanner
import fr.mescourses.app.ui.components.SectionCard
import fr.mescourses.app.ui.components.StatChip
import fr.mescourses.app.ui.retail.RetailerDot
import fr.mescourses.core.cart.LineStatus
import fr.mescourses.core.cart.PriceComparison
import fr.mescourses.core.cart.RetailerQuote
import fr.mescourses.core.model.ShoppingItem
import fr.mescourses.core.retail.Retailer
import fr.mescourses.core.shopping.ShoppingListMerger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompareState(
    val label: String = "Liste de courses",
    val items: List<ShoppingItem> = emptyList(),
    val quotes: Map<Retailer, RetailerQuote> = emptyMap(),
    val running: Boolean = false,
    val error: String? = null,
) {
    val cheapest: Retailer? get() = if (running) null else PriceComparison.cheapest(quotes.values)
}

/** Cherche la même liste chez les trois enseignes et garde, pour chacune, un produit par article. */
class CompareViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CompareState())
    val state: StateFlow<CompareState> = _state
    val preferred get() = c.settings.current.preferredRetailer
    private var job: Job? = null

    init {
        start()
    }

    /**
     * Lance la comparaison. Sans [force], une comparaison récente (< 30 min) de la même liste est reprise telle quelle :
     * aller voir le panier d'une enseigne puis revenir ne relance aucune recherche.
     */
    fun start(force: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            val pending = c.pendingCompare.value
            c.pendingCompare.value = null
            val items = ShoppingListMerger.merge(pending?.items ?: c.shopping.all())
            val label = pending?.label ?: "Liste de courses"
            val key = items.map { "${it.name}|${it.quantity}|${it.unit}|${it.source}" }
            val cached = CompareCache.state
            if (!force && cached != null && CompareCache.key == key && fr.mescourses.core.currentTimeMillis() - CompareCache.at < CACHE_MS) {
                _state.value = cached.copy(label = label)
                return@launch
            }
            if (items.isEmpty()) {
                _state.value = CompareState(label = label, error = "Rien à comparer : la liste est vide.")
                return@launch
            }
            _state.value = CompareState(label = label, items = items, running = true)
            // Les WebViews des trois enseignes sont attachées le temps de la comparaison.
            c.comparing.value = Retailer.entries.toSet()
            try {
                PriceComparison.compare(
                    backends = Retailer.entries.map { c.backend(it) },
                    items = items,
                    preferOrganic = c.settings.current.preferOrganic,
                    memory = { c.productMemory(it) },
                    ready = { b -> c.session(b.retailer).let { s -> if (s.ensureSession()) null else s.problem() } },
                    queries = c.settings.current.queryMap(),
                ).collect { q -> _state.update { it.copy(quotes = it.quotes + (q.retailer to q)) } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                c.comparing.value = emptySet()
                _state.update { it.copy(running = false) }
                // Comparaison complète (toutes les enseignes ont répondu) : gardée pour les retours sur l'écran.
                val done = _state.value
                if (done.error == null && done.quotes.size == Retailer.entries.size && done.quotes.values.all { it.done || it.problem != null }) {
                    CompareCache.key = key
                    CompareCache.state = done
                    CompareCache.at = fr.mescourses.core.currentTimeMillis()
                }
            }
        }
    }

    /** Reprend le panier proposé par cette enseigne dans l'écran Panier (enseigne active pour ce panier). */
    fun fillWith(r: Retailer) {
        val quote = _state.value.quotes[r] ?: return
        c.pendingQuote.value = PendingQuote(_state.value.label, r, quote.lines)
    }

    override fun onCleared() {
        c.comparing.value = emptySet()
    }
}

private const val CACHE_MS = 30 * 60 * 1000L

/** Dernière comparaison complète (même liste d'articles), partagée entre les ouvertures de l'écran. */
private object CompareCache {
    var key: List<String>? = null
    var state: CompareState? = null
    var at: Long = 0
}

private fun euros(v: Double) = fr.mescourses.app.ui.common.euros(v)

@Composable
fun CompareScreen(
    onBack: () -> Unit,
    onFilled: () -> Unit,
    onLogin: (Retailer) -> Unit,
    onChooseStore: (Retailer) -> Unit,
) {
    val vm = containerViewModel { CompareViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val cheapest = state.cheapest

    ScreenScaffold(title = "Comparer les prix", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${state.label} : ${state.items.size} article(s) cherchés chez Carrefour, E.Leclerc et Auchan, tes choix habituels en priorité. " +
                        "Totaux estimés (prix affichés, hors promotions en lot).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            state.error?.let { item { Text("⚠ $it", color = MaterialTheme.colorScheme.error) } }
            items(Retailer.entries.toList(), key = { it.name }) { r ->
                val q = state.quotes[r]
                QuoteCard(
                    retailer = r,
                    quote = q,
                    itemCount = state.items.size,
                    preferred = r == vm.preferred,
                    cheapest = r == cheapest,
                    saving = state.quotes.values.filter { it.done && it.problem == null && it.priced.isNotEmpty() }.map { it.total }
                        .takeIf { it.size > 1 }?.let { it.max() - it.min() },
                    onFill = { vm.fillWith(r); onFilled() },
                    onLogin = { onLogin(r) },
                    onChooseStore = { onChooseStore(r) },
                )
            }
            if (state.quotes.values.any { it.found > 0 }) {
                item { GroupLabel("Détail par article", Modifier.padding(top = 8.dp)) }
                items(state.items.indices.toList(), key = { "item-$it" }) { i -> DetailCard(state.items[i], i, state.quotes) }
            }
            if (!state.running) item {
                SoftButton("Relancer la comparaison", onClick = { vm.start(force = true) }, icon = Icons.Filled.Refresh)
            }
        }
    }
}

@Composable
private fun QuoteCard(
    retailer: Retailer,
    quote: RetailerQuote?,
    itemCount: Int,
    preferred: Boolean,
    cheapest: Boolean,
    saving: Double?,
    onFill: () -> Unit,
    onLogin: () -> Unit,
    onChooseStore: () -> Unit,
) {
    // La moins chère est mise en avant : carte brun café, badge safran.
    val bg = if (cheapest) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerLowest
    val fg = if (cheapest) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Surface(shape = RoundedCornerShape(22.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetailerDot(retailer, 36.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(retailer.label + if (preferred) " ★" else "", style = MaterialTheme.typography.titleMedium, color = fg)
                    val sub = when {
                        quote == null -> "En attente…"
                        quote.problem != null -> quote.problem!!
                        !quote.done -> "Recherche… ${quote.lines.count { it.status != LineStatus.A_CHERCHER }} / $itemCount"
                        else -> "${quote.priced.size} / $itemCount trouvés" + if (quote.missing > 0) " · ${quote.missing} introuvable(s)" else ""
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f))
                }
                if (quote != null && quote.problem == null && quote.priced.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (cheapest) Text(
                            "Moins cher",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Cuisine.Coffee,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Cuisine.Saffron).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Text(euros(quote.total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = fg)
                    }
                }
            }
            if (cheapest && saving != null && saving >= 0.01) {
                Text("${euros(saving)} d'économie sur la plus chère", style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f))
            }
            when {
                quote == null -> Unit
                quote.problem != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CtaButton("Se connecter", onClick = onLogin)
                    SoftButton("Mon drive", onClick = onChooseStore)
                }
                !quote.done -> {
                    val searched = quote.lines.count { it.status != LineStatus.A_CHERCHER }
                    LinearProgressIndicator(
                        progress = { searched.toFloat() / itemCount.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    )
                }
                quote.found > 0 -> if (cheapest) CtaButton("Remplir le panier ici", onClick = onFill, modifier = Modifier.fillMaxWidth())
                else SoftButton("Remplir le panier ici", onClick = onFill, modifier = Modifier.fillMaxWidth(), height = 44.dp)
            }
        }
    }
}

/** Un article : pour chaque enseigne, la photo du produit retenu, son nom, bio / non bio et le prix (le moins cher en avant). */
@Composable
private fun DetailCard(item: ShoppingItem, index: Int, quotes: Map<Retailer, RetailerQuote>) {
    val lines = Retailer.entries.associateWith { r -> quotes[r]?.lines?.getOrNull(index) }
    val prices = lines.mapValues { (_, l) -> l?.product?.price?.let { it * l.quantity } }
    val best = prices.values.filterNotNull().minOrNull()
    val several = prices.values.count { it != null } > 1
    WhiteCard(padding = PaddingValues(14.dp)) {
        Text(ShoppingListMerger.format(item), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Retailer.entries.forEachIndexed { n, r ->
            if (n > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val l = lines[r]
            val p = l?.product
            val price = prices[r]
            val cheapest = price != null && price == best && several
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (p != null) ProductThumb(p, 56) else Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RetailerDot(r, 14.dp)
                        Text(r.label.removePrefix("E."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        p?.name ?: if (l == null) "En attente…" else "Introuvable",
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (p == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (p != null) Text(
                        if (p.organic) "BIO" else "Non bio",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (p.organic) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (p.organic) Color(0xFF5E9B3A) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        price?.let(::euros) ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (cheapest) FontWeight.Bold else FontWeight.Normal,
                        color = if (cheapest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (cheapest) Text("le moins cher", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (l != null && l.quantity > 1) Text("× ${l.quantity}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
