package fr.mescourses.app.platform

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Ensemble sûr en accès concurrent (remplace `ConcurrentHashMap.newKeySet()` en code commun). L'itération porte
 * sur une copie : on peut le modifier pendant qu'on le parcourt.
 */
class ConcurrentSet<T> : MutableSet<T> {
    private val lock = SynchronizedObject()
    private val set = HashSet<T>()

    private fun <R> locked(block: () -> R): R = synchronized(lock, block)

    override val size: Int get() = locked { set.size }
    override fun isEmpty(): Boolean = locked { set.isEmpty() }
    override fun contains(element: T): Boolean = locked { element in set }
    override fun containsAll(elements: Collection<T>): Boolean = locked { set.containsAll(elements) }
    override fun add(element: T): Boolean = locked { set.add(element) }
    override fun addAll(elements: Collection<T>): Boolean = locked { set.addAll(elements) }
    override fun remove(element: T): Boolean = locked { set.remove(element) }
    override fun removeAll(elements: Collection<T>): Boolean = locked { set.removeAll(elements.toSet()) }
    override fun retainAll(elements: Collection<T>): Boolean = locked { set.retainAll(elements.toSet()) }
    override fun clear() = locked { set.clear() }

    override fun iterator(): MutableIterator<T> {
        val snapshot = locked { set.toList() }.iterator()
        return object : MutableIterator<T> {
            private var last: T? = null
            override fun hasNext() = snapshot.hasNext()
            override fun next(): T = snapshot.next().also { last = it }
            @Suppress("UNCHECKED_CAST")
            override fun remove() { this@ConcurrentSet.remove(last as T) }
        }
    }

    override fun equals(other: Any?): Boolean = other is Set<*> && locked { set == other }
    override fun hashCode(): Int = locked { set.hashCode() }
    override fun toString(): String = locked { set.toString() }
}
