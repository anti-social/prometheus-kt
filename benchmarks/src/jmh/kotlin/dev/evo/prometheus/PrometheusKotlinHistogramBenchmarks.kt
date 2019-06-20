package dev.evo.prometheus

import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.random.asJavaRandom

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads

open class PrometheusKotlinHistogramBenchmarks {
    class Labels : LabelSet() {
        val label1 by label("label_1")
        val label2 by label("label_2")
    }

    @State(Scope.Benchmark)
    open class Metrics : PrometheusMetrics() {
        val values = Random.asJavaRandom()
            .doubles(0.0, 21.0)
            .limit(1_000_000)
            .toArray()
        val histogramWithoutLabels by histogram("histogram", (1..20).map { it.toDouble() })
        val histogramWithLabels by histogram("histogram_with_labels", (1..20).map { it.toDouble() }) { Labels() }
    }

    @Benchmark
    @Threads(1)
    open fun histogramWithoutLabels(metrics: Metrics) = runBlocking {
        cycle {
            metrics.histogramWithoutLabels.observe(metrics.values[it])
        }
    }

    @Benchmark
    @Threads(1)
    open fun histogramWithLabels(metrics: Metrics) = runBlocking {
        cycle {
            metrics.histogramWithLabels.observe(metrics.values[it])
        }
    }
}