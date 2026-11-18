package fr.mescourses.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Kit de la refonte : grands titres, boutons ronds, pastilles de filtre, cartes blanches et lignes de réglages,
 * partagés par Recettes, Liste, Panier et Réglages (même langage que le tunnel « Envie »).
 */

/** En-tête de page : retour rond facultatif, petit sur-titre, grand titre, actions rondes à droite. */
@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    big: Boolean = onBack == null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = if (onBack != null) 16.dp else 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = if (onBack != null) Alignment.CenterVertically else Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Retour", onBack)
        Column(Modifier.weight(1f)) {
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                title,
                style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Bouton rond (44 dp) : fond blanc par défaut, pastille de compteur facultative. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    badge: Int? = null,
) {
    Box {
        Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = container, modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(size * 0.46f))
            }
        }
        if (badge != null && badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).heightIn(min = 18.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) { Text("$badge", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Pastille de filtre : brun café quand elle est choisie, blanche bordée sinon ; compteur discret facultatif. */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, count: Int? = null, enabled: Boolean = true, leading: ImageVector? = null) {
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(38.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leading?.let { Icon(it, null, tint = fg, modifier = Modifier.size(16.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
            count?.let { Text("$it", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, color = fg.copy(alpha = 0.7f)) }
        }
    }
}

/** Champ de recherche arrondi, fond blanc. */
@Composable
fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, "Effacer") }
                trailing?.invoke()
                if (trailing != null) Spacer(Modifier.width(6.dp))
            }
        },
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Carte blanche arrondie (22 dp). */
@Composable
fun WhiteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = color, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(shape = RoundedCornerShape(22.dp), color = color, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

/** Petit titre de groupe en capitales. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = color,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Pastille d'icône carrée arrondie, sur fond coloré. */
@Composable
fun IconTile(icon: ImageVector, background: Color, size: Dp = 42.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.33f)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Ligne de liste « icône · titre · valeur actuelle · chevron » (Réglages, ajout de recette). */
@Composable
fun NavRow(icon: ImageVector, iconBackground: Color, title: String, value: String?, onClick: () -> Unit, dot: Color? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, iconBackground)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            value?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dot?.let { c -> Box(Modifier.size(8.dp).clip(CircleShape).background(c)) }
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Séparateur fin à l'intérieur d'une carte (aligné après l'icône). */
@Composable
fun RowDivider(start: Dp = 70.dp) = HorizontalDivider(Modifier.padding(start = start, end = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** Bouton principal en pilule tomate (52 dp). */
@Composable
fun CtaButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.height(52.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val fg = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bouton secondaire en pilule blanche bordée (52 dp). */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, height: Dp = 52.dp) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(height),
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val fg = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
            icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Petite étiquette arrondie (type de plat, durée, site…). */
@Composable
fun Tag(text: String, background: Color = MaterialTheme.colorScheme.surfaceContainerLowest, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(14.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}
