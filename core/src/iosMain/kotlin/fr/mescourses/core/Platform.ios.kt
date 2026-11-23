package fr.mescourses.core

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun String.toNfd(): String = (this as NSString).decomposedStringWithCanonicalMapping

internal actual fun logError(message: String) = println(message)
