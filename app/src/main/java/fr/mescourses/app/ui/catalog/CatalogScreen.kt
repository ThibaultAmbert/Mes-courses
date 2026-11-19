package fr.mescourses.app.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import fr.mescourses.app.ui.components.SiteLogoBadge
import fr.mescourses.app.ui.theme.Cuisine
import fr.mescourses.app.ui.components.BusyBar
import fr.mescourses.app.ui.components.Spinner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mescourses.app.data.CatalogStatus
import fr.mescourses.app.data.CatalogSync
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.appContainer
import fr.mescourses.app.ui.components.SectionCard
import fr.mescourses.core.catalog.CatalogSources
import fr.mescourses.core.recipes.RecipeSites
import java.util.Locale

/**
 * Choix des bases de recettes à télécharger : une grille de logos, un par site. Toucher un logo télécharge la base (ou la
 * met à jour) : un anneau d'avancement se dessine alors autour du logo. Premier écran au premier lancement
 * ([firstRun]), puis accessible depuis les Réglages et depuis les filtres de recherche.
 */
@Composable
fun CatalogScreen(firstRun: Boolean, onDone: () -> Unit) {
    val c = appContainer()
    val sync = c.catalogSync
    val st by sync.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { sync.checkRemote() }

    val sites = CatalogSources.all.map { it.siteId }.sortedBy { siteName(it).lowercase() }
    val missing = sites.filter { it !in st.installed }
    val outdated = sites.filter { st.outdated(it) }
    val busy = st.working != null || st.pending.isNotEmpty()
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Bases de recettes", onBack = if (firstRun) null else onDone) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (firstRun) "Touche un site pour télécharger ses recettes sur ton téléphone : Découvrir et le menu cherchent dedans, sans réseau. " +
                    "Tu pourras en ajouter, les mettre à jour ou en retirer plus tard dans Réglages › Bases de recettes."
                else "Touche un site pour télécharger ou mettre à jour sa base. Seules les bases téléchargées sont utilisées par la recherche et le menu.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Chaque base pèse de quelques Mo à plusieurs dizaines de Mo : mieux vaut être en Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            sites.chunked(GRID_COLUMNS).forEach { rowSites ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowSites.forEach { id ->
                        SiteTile(
                            id, st,
                            onClick = {
                                when {
                                    st.installing == id || id in st.pending -> Unit
                                    id !in st.installed || st.outdated(id) -> sync.install(listOf(id))
                                    else -> confirmRemove = id
                                }
                            },
                            onLongClick = { if (id in st.installed && st.installing != id) confirmRemove = id },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Dernière ligne incomplète : on garde la largeur des cases.
                    repeat(GRID_COLUMNS - rowSites.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Legend()

            if (missing.isNotEmpty() || outdated.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (missing.isNotEmpty()) Button(onClick = { sync.install(missing) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(if (missing.size == sites.size) "Tout télécharger" else "Télécharger les ${missing.size} restantes")
                    }
                    if (outdated.isNotEmpty()) OutlinedButton(onClick = { sync.install(outdated) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(if (outdated.size == 1) "Mettre à jour" else "Tout mettre à jour (${outdated.size})")
                    }
                }
            }

            st.working?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (st.working == null) st.lastResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (firstRun) {
                Button(onClick = onDone, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (st.installed.isEmpty()) "Passer pour l'instant" else "Continuer")
                }
                if (st.installed.isEmpty()) Text(
                    "Sans base, la recherche se fait en ligne, site par site.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    confirmRemove?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Supprimer la base ${siteName(id)} ?") },
            text = {
                Text(
                    "Ses %,d recettes seront retirées du téléphone : Découvrir et le menu ne les trouveront plus. ".format(Locale.FRANCE, st.bySite[id] ?: 0) +
                        "Tes favoris et ton historique ne changent pas. Tu pourras la télécharger de nouveau.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmRemove = null; sync.remove(id) }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Annuler") } },
        )
    }
}

private const val GRID_COLUMNS = 3

/** Une case de la grille : logo, anneau d'avancement pendant le téléchargement, pastille d'état et nom du site. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(id: String, st: CatalogStatus, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val installed = id in st.installed
    val downloading = st.installing == id
    val waiting = id in st.pending
    val outdated = st.outdated(id)
    val name = siteName(id)
    val size = st.remote[id]?.bytes?.takeIf { it > 0 }?.let { mb(it) }
    val progress = st.installProgress

    val subtitle = when {
        downloading -> progress?.let { "${(it * 100).toInt()} %" } ?: "Téléchargement…"
        waiting -> "En attente…"
        installed -> "%,d recettes".format(Locale.FRANCE, st.bySite[id] ?: 0)
        else -> size ?: "Non téléchargée"
    }
    val stateText = when {
        downloading -> "téléchargement en cours"
        waiting -> "en attente"
        installed && outdated -> "mise à jour disponible"
        installed -> "à jour"
        else -> "non téléchargée"
    }

    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { contentDescription = "$name, $stateText" }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(LOGO_BOX), contentAlignment = Alignment.Center) {
            // Une base absente est grisée : elle « s'allume » une fois téléchargée.
            SiteLogoBadge(id, name, LOGO_SIZE, alpha = if (installed || downloading) 1f else 0.45f)
            when {
                downloading -> {
                    if (progress != null) CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(LOGO_BOX),
                        strokeWidth = 4.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) else Spinner(Modifier.size(LOGO_BOX), strokeWidth = 4.dp, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
                waiting -> CircularProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.size(LOGO_BOX),
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            val badge: Triple<ImageVector, Color, Color>? = when {
                downloading || waiting -> null
                installed && outdated -> Triple(Icons.Filled.Sync, Cuisine.Saffron, Cuisine.Coffee)
                installed -> Triple(Icons.Filled.Check, Cuisine.Basil, Cuisine.Coffee)
                else -> Triple(Icons.Filled.Download, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            }
            if (badge != null) Box(
                Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(badge.second),
                contentAlignment = Alignment.Center,
            ) { Icon(badge.first, null, Modifier.size(15.dp), tint = badge.third) }
        }
        Text(name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (installed && outdated && !downloading) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val LOGO_BOX = 80.dp
private val LOGO_SIZE = 68.dp

@Composable
private fun Legend() {
    Text(
        "Coche verte : à jour · Pastille orange : mise à jour disponible · Appui long sur une base installée : la supprimer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Carte « Recherche par le sens » (modèle EmbeddingGemma), affichée dans Réglages › Le chef. */
@Composable
fun SemanticSearchCard() {
    val c = appContainer()
    val sync = c.catalogSync
    val st by sync.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { sync.checkRemote() }
    EmbedderRow(st, onInstall = { sync.installEmbedder() }, onCancel = { sync.cancelEmbedder() }, onRemove = { sync.removeEmbedder(c.embedder) })
}

@Composable
internal fun EmbedderRow(st: CatalogStatus, onInstall: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit) {
    val sizeBytes = st.embedderRemote?.bytes?.takeIf { it > 0 } ?: CatalogSync.DEFAULT_EMBEDDER_BYTES
    val sizeLabel = mb(sizeBytes)
    var confirmRemove by remember { mutableStateOf(false) }
    SectionCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Recherche par le sens · $sizeLabel", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        st.embedderDownloading && st.embedderTotal > 0 ->
                            "Téléchargement… ${mb(st.embedderDownloaded)} / ${mb(st.embedderTotal)}"
                        st.embedderDownloading ->
                            "Téléchargement… ${mb(st.embedderDownloaded)}"
                        st.embedderInstalled ->
                            "Installée sur le téléphone · comprend les envies"
                        else ->
                            "Non téléchargée · comprend les envies (ex. « réconfortant », « léger »)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                st.embedderDownloading -> TextButton(onClick = onCancel) { Text("Annuler") }
                st.embedderInstalled -> TextButton(onClick = { confirmRemove = true }) { Text("Supprimer") }
                else -> Button(onClick = onInstall) { Text("Télécharger") }
            }
        }
        if (st.embedderDownloading) {
            if (st.embedderTotal > 0) {
                val progress = (st.embedderDownloaded.toFloat() / st.embedderTotal.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                BusyBar(Modifier.fillMaxWidth())
            }
        }
        st.embedderError?.let { err ->
            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Supprimer la recherche par le sens ?") },
        text = {
            Text(
                "Le modèle ($sizeLabel) sera supprimé du téléphone. La recherche par mots-clés reste disponible et tu pourras le télécharger de nouveau.",
            )
        },
        confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Annuler") } },
    )
}

internal fun siteName(id: String) = RecipeSites.byId(id)?.name ?: id

internal fun mb(bytes: Long) = if (bytes < 1_000_000) "<1 Mo" else "%.0f Mo".format(Locale.FRANCE, bytes / 1_000_000.0)
