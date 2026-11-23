package fr.mescourses.core

import java.text.Normalizer

internal actual fun String.toNfd(): String = Normalizer.normalize(this, Normalizer.Form.NFD)

internal actual fun logError(message: String) = System.err.println(message)
