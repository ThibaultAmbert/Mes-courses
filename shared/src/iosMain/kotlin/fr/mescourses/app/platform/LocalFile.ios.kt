package fr.mescourses.app.platform

import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber

actual fun freeSpaceBytes(path: String): Long =
    (NSFileManager.defaultManager.attributesOfFileSystemForPath(path, null)?.get(NSFileSystemFreeSize) as? NSNumber)
        ?.longLongValue ?: 0L
