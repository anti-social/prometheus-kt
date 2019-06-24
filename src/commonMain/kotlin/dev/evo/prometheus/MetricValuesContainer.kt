package dev.evo.prometheus

internal expect class MetricValuesContainer() {
    val estimatedSamplesCount: Int

    suspend fun getOrPut(key: MetricKey, init: () -> MetricValue): MetricValue

    suspend fun forEach(block: (Pair<MetricKey, MetricValue>) -> Unit)
}
