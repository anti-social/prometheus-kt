package dev.evo.prometheus

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal actual class Registry<K, V> {
    private val locks = Array(CONCURRENCY_LEVEL) { Mutex() }
    private val registry = Array(CONCURRENCY_LEVEL) { HashMap<K, V>() }

    companion object {
        private const val CONCURRENCY_LEVEL = 16
    }

    private fun getIndex(key: K) = (key.hashCode() and 0x7FFF_FFFF) % CONCURRENCY_LEVEL

    actual val size = registry.size

    actual suspend fun getOrPut(key: K, init: () -> V): V {
        val ix = getIndex(key)
        return locks[ix].withLock {
            registry[ix].computeIfAbsent(key) { init() }
        }
    }

    actual suspend fun forEach(block: (Pair<K, V>) -> Unit) {
        (0 until CONCURRENCY_LEVEL).forEach { ix ->
            locks[ix].withLock {
                for (entry in registry[ix]) {
                    block(entry.toPair())
                }
            }
        }
    }
}
