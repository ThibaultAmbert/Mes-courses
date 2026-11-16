package fr.mescourses.core

import kotlin.coroutines.cancellation.CancellationException

/** Équivalent de [runCatching] qui laisse toujours remonter [CancellationException] des coroutines. */
inline fun <R> runCatchingCancellable(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    Result.failure(t)
}
