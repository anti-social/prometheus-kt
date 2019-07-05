package dev.evo.prometheus.util

import dev.evo.prometheus.MetricKey
import dev.evo.prometheus.MetricValue

internal expect class MetricValuesContainer() {
    val estimatedSamplesCount: Int

    suspend fun getOrPut(key: MetricKey, init: () -> MetricValue): MetricValue

    suspend fun forEach(block: (Pair<MetricKey, MetricValue>) -> Unit)
}
