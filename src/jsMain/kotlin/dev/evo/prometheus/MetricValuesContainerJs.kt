package dev.evo.prometheus

/**
 * Not thread-safe because JS and Native (at the moment) have not support for parallelism of coroutines.
 */
internal actual class MetricValuesContainer : SingleThreadedMetricValuesContainer()
