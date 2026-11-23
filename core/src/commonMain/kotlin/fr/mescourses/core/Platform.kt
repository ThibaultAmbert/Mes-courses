package fr.mescourses.core

/**
 * Les rares points où le code commun a besoin de la plateforme (JVM pour Android et les outils, iOS).
 * Tout le reste de `core` est du Kotlin pur.
 */

/** Forme Unicode NFD (« é » → « e » + accent combinant), pour retirer les accents. */
internal expect fun String.toNfd(): String

/** Message de diagnostic (sortie d'erreur sur la JVM, console sur iOS). */
internal expect fun logError(message: String)
