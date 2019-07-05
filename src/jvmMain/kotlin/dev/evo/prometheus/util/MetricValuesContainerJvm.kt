package dev.evo.prometheus.util

import dev.evo.prometheus.MetricKey
import dev.evo.prometheus.MetricValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.LongAdder

internal actual class MetricValuesContainer {
    private val locks = Array(CONCURRENCY_LEVEL) { Mutex() }
    private val values = Array(CONCURRENCY_LEVEL) { HashMap<MetricKey, MetricValue>() }
    private val valuesSize = LongAdder()

    companion object {
        private const val CONCURRENCY_LEVEL = 16
    }

    private fun getIndex(key: MetricKey) = (key.hashCode() and 0x7FFF_FFFF) % CONCURRENCY_LEVEL

    actual val estimatedSamplesCount: Int
        get() {
            return valuesSize.sum().toInt()
        }

    actual suspend fun getOrPut(key: MetricKey, init: () -> MetricValue): MetricValue {
        val ix = getIndex(key)
        val map = values[ix]
        return locks[ix].withLock {
            map[key] ?: init().also {
                map[key] = it
                valuesSize.increment()
            }
        }
    }

    actual suspend fun forEach(block: (Pair<MetricKey, MetricValue>) -> Unit) {
        (0 until CONCURRENCY_LEVEL).forEach { ix ->
            locks[ix].withLock {
                for (entry in values[ix]) {
                    block(entry.toPair())
                }
            }
        }
    }
}
