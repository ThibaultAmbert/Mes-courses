package fr.mescourses.app.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bascule de l'onglet « Courses » : En magasin (la liste à cocher) ou Au drive (le panier de l'enseigne).
 * Les deux modes gardent leur état : on passe de l'un à l'autre sans rien perdre.
 */
@Composable
fun CoursesSwitch(drive: Boolean, onStore: () -> Unit, onDrive: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Segment("En magasin", Icons.Filled.Checklist, selected = !drive, onClick = onStore, modifier = Modifier.weight(1f))
        Segment("Au drive", Icons.Filled.ShoppingCart, selected = drive, onClick = onDrive, modifier = Modifier.weight(1f))
    }
}

/** Un choix d'une bascule en pilule (Courses, Apparence…). */
@Composable
fun Segment(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = { if (!selected) onClick() },
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        modifier = modifier.height(42.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Box(Modifier.size(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}
