package dev.evo.prometheus

/**
 * Not thread-safe because JS and Native (at the moment) does not support parallelism for coroutines.
 */
internal open class SingleThreadedMetricValuesContainer {
    private val values = HashMap<MetricKey, MetricValue>()

    private var samplesCount = 0
    val estimatedSamplesCount: Int
        get() = samplesCount

    suspend fun getOrPut(key: MetricKey, init: () -> MetricValue): MetricValue {
        return values[key] ?: init().also {
            values[key] = it
            samplesCount += it.numSamples
        }
    }

    suspend fun forEach(block: (Pair<MetricKey, MetricValue>) -> Unit) {
        for (entry in values) {
            block(entry.key to entry.value)
        }
    }
}
