package fr.mescourses.app.platform

/** Entier avec séparateur de milliers à la française (espace fine insécable), comme `"%,d".format(Locale.FRANCE, n)`. */
fun formatThousands(n: Long): String {
    val digits = kotlin.math.abs(n).toString()
    val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
    return if (n < 0) "-$grouped" else grouped
}

fun formatThousands(n: Int): String = formatThousands(n.toLong())
