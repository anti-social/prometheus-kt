package dev.evo.prometheus.util

import dev.evo.prometheus.MetricKey
import dev.evo.prometheus.MetricValue
import kotlinx.atomicfu.atomic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal open class ConcurrentMetricValuesContainer {
    private val locks = Array(CONCURRENCY_LEVEL) { Mutex() }
    private val values = Array(CONCURRENCY_LEVEL) { HashMap<MetricKey, MetricValue>() }
    private val valuesSize = atomic(0)

    companion object {
        private const val CONCURRENCY_LEVEL = 16
    }

    private fun getIndex(key: MetricKey) = (key.hashCode() and 0x7FFF_FFFF) % CONCURRENCY_LEVEL

    val estimatedSamplesCount: Int
        get() {
            return valuesSize.value
        }

    suspend fun getOrPut(key: MetricKey, init: () -> MetricValue): MetricValue {
        val ix = getIndex(key)
        val map = values[ix]
        return locks[ix].withLock {
            map[key] ?: init().also {
                map[key] = it
                valuesSize.incrementAndGet()
            }
        }
    }

    suspend fun forEach(block: (Pair<MetricKey, MetricValue>) -> Unit) {
        (0 until CONCURRENCY_LEVEL).forEach { ix ->
            locks[ix].withLock {
                for (entry in values[ix]) {
                    block(entry.toPair())
                }
            }
        }
    }
}
