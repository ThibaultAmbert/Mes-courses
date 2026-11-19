package fr.mescourses.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.mescourses.app.R
import fr.mescourses.app.ui.theme.Cuisine

/** Logos des sites, embarqués dans l'appli (aucun réseau) ; sans logo, on affiche les initiales du site. */
val SITE_LOGOS = mapOf(
    "marmiton" to R.drawable.logo_marmiton,
    "jow" to R.drawable.logo_jow,
    "hellofresh" to R.drawable.logo_hellofresh,
    "cuisineaz" to R.drawable.logo_cuisineaz,
    "750g" to R.drawable.logo_750g,
    "jdf" to R.drawable.logo_jdf,
    "picnic" to R.drawable.logo_picnic,
)

/** Pastille ronde blanche avec le logo du site (ou ses initiales). */
@Composable
fun SiteLogoBadge(siteId: String, name: String, size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color.White).alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        val logo = SITE_LOGOS[siteId]
        if (logo != null) Image(
            painter = painterResource(logo),
            contentDescription = name,
            modifier = Modifier.size(size * 0.62f).clip(RoundedCornerShape(6.dp)),
        ) else Text(
            name.split(' ').joinToString("") { it.take(1) }.take(3),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Cuisine.Coffee,
        )
    }
}
