package fr.mescourses.app.platform

/** Journal de diagnostic (Logcat sur Android, console sur iOS). */
expect object Log {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, error: Throwable? = null)
}
