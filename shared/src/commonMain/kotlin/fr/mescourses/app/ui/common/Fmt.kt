package fr.mescourses.app.ui.common

import fr.mescourses.app.platform.formatThousands
import fr.mescourses.core.formatDecimal
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * Formats affichés à la française (virgule décimale, espace fine pour les milliers), identiques à ce que donnaient
 * String.format sur un téléphone réglé en français.
 */

/** Nombre à [decimals] décimales avec une virgule (« 3,6 »). */
fun dec(value: Double, decimals: Int): String = formatDecimal(value, decimals, ',')

/** Prix en euros (« 12,34 € »). */
fun euros(value: Double): String = "${dec(value, 2)} €"

/** Entier avec séparateur de milliers (« 12 345 »). */
fun thousands(n: Int): String = formatThousands(n)
fun thousands(n: Long): String = formatThousands(n)

/** Entier sur deux chiffres (« 05 »). */
fun twoDigits(n: Int): String = n.toString().padStart(2, '0')

private val MONTHS = listOf(
    "janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre",
)

/** Date et heure locales (« 3 novembre à 08:05 »), comme `SimpleDateFormat("d MMMM à HH:mm", FRANCE)`. */
fun frenchDayTime(epochMillis: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.day} ${MONTHS[t.month.number - 1]} à ${twoDigits(t.hour)}:${twoDigits(t.minute)}"
}
