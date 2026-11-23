package fr.mescourses.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock

/*
 * Équivalents en Kotlin pur (toutes plateformes) des quelques outils de la JVM qu'utilisait `core`.
 * Ils reproduisent exactement le comportement d'avant pour que rien ne change côté Android.
 */

/** Heure courante en millisecondes depuis 1970 (comme `System.currentTimeMillis()`). */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Date du jour dans le fuseau du téléphone (comme `LocalDate.now()`). */
fun todayLocal(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(weeks: Int): LocalDate = plus(weeks * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(weeks: Int): LocalDate = plus(-weeks * 7, DateTimeUnit.DAY)
fun LocalDate.plusYears(years: Int): LocalDate = plus(years, DateTimeUnit.YEAR)

/** Semaine « alignée » de l'année : jours 1-7 → 1, 8-14 → 2… (`ChronoField.ALIGNED_WEEK_OF_YEAR`). */
val LocalDate.alignedWeekOfYear: Int get() = (dayOfYear - 1) / 7 + 1

/**
 * Encodage d'URL identique à `java.net.URLEncoder.encode(s, UTF_8)` : lettres, chiffres et `.-*_` gardés,
 * espace → `+` (ou `%20` si [spaceAsPlus] est faux), le reste en `%XX` (UTF-8, majuscules).
 */
fun urlEncode(value: String, spaceAsPlus: Boolean = true): String = buildString {
    for (b in value.encodeToByteArray()) {
        val c = (b.toInt() and 0xFF)
        val ch = c.toChar()
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '-' || ch == '*' || ch == '_' -> append(ch)
            ch == ' ' -> append(if (spaceAsPlus) "+" else "%20")
            else -> {
                append('%')
                append(HEX[c shr 4])
                append(HEX[c and 0x0F])
            }
        }
    }
}

private const val HEX = "0123456789ABCDEF"

/** Caractère(s) du point de code Unicode [cp], ou null s'il n'est pas valide (`Character.toChars`). */
fun codePointToString(cp: Int): String? = when {
    cp < 0 || cp > 0x10FFFF -> null
    cp < 0x10000 -> cp.toChar().toString()
    else -> {
        val v = cp - 0x10000
        charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }
}

/**
 * Nombre à [decimals] décimales arrondi au plus proche (moitié vers le haut, comme `String.format("%.2f")`),
 * avec [separator] comme séparateur décimal.
 */
fun formatDecimal(value: Double, decimals: Int, separator: Char = '.'): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / factor
    if (decimals == 0) return "$sign$whole"
    val frac = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$whole$separator$frac"
}

/**
 * Résout un lien [href] par rapport à l'adresse [base] (RFC 3986, comme `java.net.URI.resolve`).
 * Rend [href] tel quel si [base] n'est pas une adresse absolue.
 */
fun resolveUrl(base: String, href: String): String {
    val ref = href.trim()
    if (SCHEME.containsMatchIn(ref)) return ref
    val m = ABSOLUTE.matchEntire(base) ?: return ref
    val scheme = m.groupValues[1]
    val authority = m.groupValues[2]
    val basePath = m.groupValues[3]
    val baseQuery = m.groups[4]?.value
    return when {
        ref.startsWith("//") -> "$scheme:$ref"
        ref.isEmpty() -> "$scheme://$authority$basePath${baseQuery?.let { "?$it" }.orEmpty()}"
        ref.startsWith("#") -> "$scheme://$authority$basePath${baseQuery?.let { "?$it" }.orEmpty()}$ref"
        ref.startsWith("?") -> "$scheme://$authority$basePath$ref"
        else -> {
            val pathEnd = ref.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) ref.length else it }
            val refPath = ref.substring(0, pathEnd)
            val rest = ref.substring(pathEnd)
            val merged = when {
                refPath.startsWith("/") -> refPath
                authority.isNotEmpty() && basePath.isEmpty() -> "/$refPath"
                else -> basePath.substringBeforeLast('/', "") + "/" + refPath
            }
            "$scheme://$authority${removeDotSegments(merged)}$rest"
        }
    }
}

private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private val ABSOLUTE = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#.*)?$""")

private fun removeDotSegments(path: String): String {
    val out = ArrayList<String>()
    val segments = path.split('/')
    segments.forEachIndexed { i, s ->
        val last = i == segments.lastIndex
        when (s) {
            "." -> if (last) out.add("")
            ".." -> {
                if (out.size > 1) out.removeAt(out.lastIndex)
                if (last) out.add("")
            }
            else -> out.add(s)
        }
    }
    return out.joinToString("/").let { if (path.startsWith("/") && !it.startsWith("/")) "/$it" else it }
}

/** Hôte d'une adresse absolue (« https://www.marmiton.org/x » → « www.marmiton.org »), comme `URI.host`. */
fun urlHost(url: String): String? = HOST.find(url)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

private val HOST = Regex("""^[A-Za-z][A-Za-z0-9+.-]*://(?:[^@/?#]*@)?([^:/?#\s]*)""")

/** `Map.putIfAbsent` de la JVM : ajoute seulement si la clé manque (ou vaut null) ; rend l'ancienne valeur. */
fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? {
    val old = get(key)
    if (old == null) put(key, value)
    return old
}

/** `Map.merge` de la JVM : [value] si la clé manque, sinon [remapping] (ancienne, [value]) ; null retire la clé. */
fun <K, V : Any> MutableMap<K, V>.merge(key: K, value: V, remapping: (V, V) -> V?): V? {
    val old = get(key)
    val new = if (old == null) value else remapping(old, value)
    if (new == null) remove(key) else put(key, new)
    return new
}
