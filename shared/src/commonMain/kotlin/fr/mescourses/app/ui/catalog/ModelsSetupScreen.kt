package fr.mescourses.app.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mescourses.app.llm.DownloadState
import fr.mescourses.app.llm.ModelCatalog
import fr.mescourses.app.llm.ModelInfo
import fr.mescourses.app.ui.common.ScreenScaffold
import fr.mescourses.app.ui.common.appContainer
import fr.mescourses.app.ui.components.SectionCard
import fr.mescourses.app.ui.common.dec

/**
 * Deuxième écran du premier lancement : les modèles qui tournent sur le téléphone (le chef, un modèle de langage, et la
 * recherche par le sens). Les téléchargements continuent en arrière-plan si on quitte l'écran.
 */
@Composable
fun ModelsSetupScreen(onDone: () -> Unit) {
    val c = appContainer()
    val sync = c.catalogSync
    val st by sync.status.collectAsStateWithLifecycle()
    val download by c.models.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { sync.checkRemote() }
    // La liste des fichiers installés est relue à chaque fin de téléchargement.
    val installedFiles = remember((download as? DownloadState.Done)?.path) { c.models.installed().map { it.name }.toSet() }
    val anyDownload = download is DownloadState.Running || st.embedderDownloading

    ScreenScaffold(title = "Intelligence du téléphone") { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Deux modèles tournent entièrement sur ton téléphone, sans envoyer tes données : le chef (il compose les menus et " +
                    "discute avec toi) et la recherche par le sens (elle comprend les envies). Aucun n'est obligatoire.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Ces fichiers sont lourds (de 1 à plusieurs Go) : mieux vaut être en Wi-Fi. Tu peux continuer, le téléchargement " +
                    "se poursuit en arrière-plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Le chef · modèle de langage", style = MaterialTheme.typography.titleMedium)
            ModelCatalog.models.forEach { m ->
                LlmRow(
                    m,
                    installed = m.fileName in installedFiles,
                    download = download,
                    onInstall = { c.models.download(m, hfToken = c.settings.current.hfToken) { path -> c.selectModel(path) } },
                    onCancel = { c.models.cancel() },
                )
            }
            (download as? DownloadState.Failed)?.let {
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Text("Recherche par le sens", style = MaterialTheme.typography.titleMedium)
            EmbedderRow(st, onInstall = { sync.installEmbedder() }, onCancel = { sync.cancelEmbedder() }, onRemove = { sync.removeEmbedder(c.embedder) })

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        anyDownload -> "Continuer (téléchargement en arrière-plan)"
                        installedFiles.isEmpty() && !st.embedderInstalled -> "Passer pour l'instant"
                        else -> "Terminer"
                    },
                )
            }
            Text(
                "Tu pourras les télécharger plus tard dans Réglages › Le chef.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LlmRow(m: ModelInfo, installed: Boolean, download: DownloadState, onInstall: () -> Unit, onCancel: () -> Unit) {
    val running = (download as? DownloadState.Running)?.takeIf { it.label == m.label }
    val otherRunning = download is DownloadState.Running && running == null
    SectionCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${m.label} · ≈ ${m.approxSizeGb} Go", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        running != null && running.total > 0 -> "Téléchargement… ${dec(running.downloaded / 1e9, 2)} / ${dec(running.total / 1e9, 2)} Go"
                        running != null -> "Téléchargement…"
                        installed -> "Installé sur le téléphone"
                        else -> m.description
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                running != null -> TextButton(onClick = onCancel) { Text("Annuler") }
                installed -> Unit
                else -> Button(onClick = onInstall, enabled = !otherRunning) { Text("Télécharger") }
            }
        }
        if (running != null) LinearProgressIndicator(progress = { running.progress }, modifier = Modifier.fillMaxWidth())
    }
}
