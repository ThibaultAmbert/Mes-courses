package fr.mescourses.app.platform

import platform.Foundation.NSLog

actual object Log {
    actual fun d(tag: String, message: String) {
        NSLog("%@", "[$tag] $message")
    }

    actual fun w(tag: String, message: String, error: Throwable?) {
        NSLog("%@", "[$tag] ⚠ $message${error?.let { " : $it" } ?: ""}")
    }
}
