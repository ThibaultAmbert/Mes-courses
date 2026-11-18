package fr.mescourses.app.ui.retail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.mescourses.core.retail.Retailer

/** Couleur repère de chaque enseigne (pastille), sans logo ni marque graphique. */
fun retailerColor(r: Retailer): Color = when (r) {
    Retailer.CARREFOUR -> Color(0xFF1E5BC6)
    Retailer.LECLERC -> Color(0xFF0B6FB8)
    Retailer.AUCHAN -> Color(0xFFD6001C)
}

/** Pastille ronde avec l'initiale de l'enseigne. */
@Composable
fun RetailerDot(r: Retailer, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(retailerColor(r)), contentAlignment = Alignment.Center) {
        Text(r.label.removePrefix("E.").take(1), color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

/** Choix de l'enseigne (★ = enseigne préférée des Réglages). */
@Composable
fun RetailerChips(selected: Retailer, preferred: Retailer?, enabled: Boolean = true, onSelect: (Retailer) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Retailer.entries.forEach { r ->
            FilterChip(
                selected = r == selected,
                onClick = { onSelect(r) },
                enabled = enabled,
                label = { Text(r.label + if (r == preferred) " ★" else "") },
                leadingIcon = { RetailerDot(r, 18.dp) },
            )
        }
    }
}

/** Bascule segmentée des enseignes (Carrefour · Leclerc · Auchan), pastille de couleur devant chaque nom. */
@Composable
fun RetailerSwitch(selected: Retailer, preferred: Retailer?, enabled: Boolean = true, onSelect: (Retailer) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Retailer.entries.forEach { r ->
            val on = r == selected
            androidx.compose.material3.Surface(
                onClick = { if (!on) onSelect(r) },
                enabled = enabled,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                color = if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(retailerColor(r)))
                    Text(
                        r.label.removePrefix("E.") + if (r == preferred) " ★" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
