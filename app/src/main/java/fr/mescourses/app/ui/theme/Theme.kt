package fr.mescourses.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mescourses.core.visual.DishVisuals

/** Palette « Cuisine » : tomate, safran, basilic, crème et brun café. */
object Cuisine {
    val Tomato = Color(0xFFC73E22)
    val Saffron = Color(0xFFF6B93B)
    val SaffronLight = Color(0xFFFDE7B0)
    val Basil = Color(0xFFCDE7A6)
    val BasilLight = Color(0xFFE3F1CF)
    val Cream = Color(0xFFFFF6EC)
    val Peach = Color(0xFFFFE9D6)
    val Coffee = Color(0xFF3B2418)
    val CoffeeMuted = Color(0xFF7A5E4E)
}

private val Light = lightColorScheme(
    primary = Cuisine.Tomato, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Cuisine.Coffee,
    secondary = Color(0xFF8A5A00), onSecondary = Color.White,
    secondaryContainer = Cuisine.SaffronLight, onSecondaryContainer = Cuisine.Coffee,
    tertiary = Color(0xFF4E7A2E), onTertiary = Color.White,
    tertiaryContainer = Cuisine.BasilLight, onTertiaryContainer = Color(0xFF1F3310),
    background = Cuisine.Cream, onBackground = Cuisine.Coffee,
    surface = Cuisine.Cream, onSurface = Cuisine.Coffee,
    surfaceVariant = Color(0xFFF6E8DA), onSurfaceVariant = Cuisine.CoffeeMuted,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF0E2),
    surfaceContainer = Color(0xFFFBEBDD), surfaceContainerHigh = Color(0xFFF6E2CF),
    surfaceContainerHighest = Color(0xFFF0D9C3),
    outline = Color(0xFFA88B78), outlineVariant = Color(0xFFEED9C4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A0), onPrimary = Color(0xFF5E1605),
    primaryContainer = Color(0xFF8E2A14), onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFF6C25B), onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4300), onSecondaryContainer = Cuisine.SaffronLight,
    tertiary = Color(0xFFB5D88E), onTertiary = Color(0xFF1F3310),
    tertiaryContainer = Color(0xFF365322), onTertiaryContainer = Cuisine.BasilLight,
    background = Color(0xFF1E1611), onBackground = Color(0xFFF6E7DA),
    surface = Color(0xFF1E1611), onSurface = Color(0xFFF6E7DA),
    surfaceVariant = Color(0xFF52433A), onSurfaceVariant = Color(0xFFD9C2B2),
    surfaceContainerLowest = Color(0xFF18110C), surfaceContainerLow = Color(0xFF261D17),
    surfaceContainer = Color(0xFF2B211B), surfaceContainerHigh = Color(0xFF362B24),
    surfaceContainerHighest = Color(0xFF41352D),
    outline = Color(0xFFA08D80), outlineVariant = Color(0xFF52433A),
)

private val Base = Typography()
private val Rounded = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = Rounded, fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Rounded, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Rounded, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Rounded, fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontFamily = Rounded, fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontFamily = Rounded, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontFamily = Rounded, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 20.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Mode nuit effectif pour un réglage d'apparence (« auto », « jour », « nuit »). */
@Composable
fun darkFor(mode: String): Boolean = when (mode) {
    "jour" -> false
    "nuit" -> true
    else -> isSystemInDarkTheme()
}

/** Le thème affiché est-il sombre ? (suit le choix Jour / Nuit, pas seulement le téléphone) */
val isDarkTheme: Boolean @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun MesCoursesTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Dégradé d'illustration selon la catégorie du plat. */
fun gradientFor(category: DishVisuals.Category, dark: Boolean): List<Color> = when (category) {
    DishVisuals.Category.LEGUMES -> listOf(Color(0xFFCFE6C4), Color(0xFF8DBF7F))
    DishVisuals.Category.POISSON -> listOf(Color(0xFFCFE6EA), Color(0xFF7FB2BE))
    DishVisuals.Category.VIANDE -> listOf(Color(0xFFF6D3C2), Color(0xFFD98B66))
    DishVisuals.Category.VEGETARIEN -> listOf(Color(0xFFFBE7B8), Color(0xFFE6B95A))
    DishVisuals.Category.SOUPE -> listOf(Color(0xFFFCE0C2), Color(0xFFEFA567))
    DishVisuals.Category.DESSERT -> listOf(Color(0xFFF8D6DE), Color(0xFFE29AAE))
    DishVisuals.Category.AUTRE -> listOf(Color(0xFFEDE6D6), Color(0xFFC9BC9C))
}.let { colors -> if (dark) colors.map { it.copy(alpha = 0.55f) } else colors }

val TitleStyle: TextStyle @Composable get() = MaterialTheme.typography.titleMedium
