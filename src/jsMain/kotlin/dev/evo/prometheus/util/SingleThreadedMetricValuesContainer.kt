package dev.evo.prometheus.util

import dev.evo.prometheus.MetricKey
import dev.evo.prometheus.MetricValue

/**
 * Not thread-safe because JS does not support parallelism.
 */
internal open class SingleThreadedMetricValuesContainer {
    private val values = HashMap<MetricKey, MetricValue>()

    private var samplesCount = 0
    val estimatedSamplesCount: Int
        get() = samplesCount


    suspend fun get(key: MetricKey): MetricValue? {
        return values[key]
    }

    suspend fun getOrPut(key: MetricKey, init: () -> MetricValue): MetricValue {
        return values[key] ?: init().also {
            values[key] = it
            samplesCount += it.numSamples
        }
    }

    suspend fun forEach(block: (MetricKey, MetricValue) -> Unit) {
        for (entry in values) {
            block(entry.key, entry.value)
        }
    }
}
