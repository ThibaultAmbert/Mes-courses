package fr.mescourses.app.platform

actual fun freeSpaceBytes(path: String): Long = java.io.File(path).usableSpace
