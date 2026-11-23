package fr.mescourses.app.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /** Contenu fixe sous le titre (bascule, onglets…). */
    below: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            androidx.compose.foundation.layout.Column {
            fr.mescourses.app.ui.components.PageTitle(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                actions = {
                    actions()
                    if (onOpenSettings != null) fr.mescourses.app.ui.components.RoundIconButton(Icons.Filled.Settings, "Réglages", onOpenSettings)
                },
            )
            below()
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = bottomBar,
        content = content,
    )
}
