package fr.mescourses.app.platform

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** Fichier local désigné par son chemin absolu (petite partie de l'API de java.io.File, en okio). */
class LocalFile(val path: String) {
    constructor(parent: LocalFile, name: String) : this(parent.path.trimEnd('/') + "/" + name)

    private val p: Path get() = path.toPath()

    val name: String get() = p.name
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val absolutePath: String get() = path
    val parentFile: LocalFile? get() = p.parent?.let { LocalFile(it.toString()) }

    fun exists(): Boolean = FileSystem.SYSTEM.exists(p)
    val isFile: Boolean get() = FileSystem.SYSTEM.metadataOrNull(p)?.isRegularFile == true
    fun length(): Long = FileSystem.SYSTEM.metadataOrNull(p)?.size ?: 0L

    fun delete(): Boolean = runCatching { FileSystem.SYSTEM.delete(p, mustExist = false) }.isSuccess
    fun deleteRecursively(): Boolean = runCatching { FileSystem.SYSTEM.deleteRecursively(p, mustExist = false) }.isSuccess
    fun mkdirs(): LocalFile = apply { runCatching { FileSystem.SYSTEM.createDirectories(p) } }

    /** Fichiers du dossier (vide s'il n'existe pas). */
    fun listFiles(): List<LocalFile> =
        runCatching { FileSystem.SYSTEM.list(p).map { LocalFile(it.toString()) } }.getOrDefault(emptyList())

    fun renameTo(target: LocalFile): Boolean = runCatching { FileSystem.SYSTEM.atomicMove(p, target.p) }.isSuccess

    override fun equals(other: Any?) = other is LocalFile && other.path == path
    override fun hashCode() = path.hashCode()
    override fun toString() = path
}

/** Place libre (octets) sur le volume qui contient [path]. */
expect fun freeSpaceBytes(path: String): Long
