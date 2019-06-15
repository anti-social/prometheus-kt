package dev.evo.prometheus

import io.prometheus.client.Histogram

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads

import kotlin.random.Random
import kotlin.random.asJavaRandom

@State(Scope.Benchmark)
open class SimpleJavaClientHistogramBenchmarks {
    private val randoms = Random.asJavaRandom()
        .doubles(0.0, 21.0)
        .limit(1_000_000)
        .toArray()
    private val histogram = Histogram.build()
        .buckets(*(1..20).map { it.toDouble() }.toDoubleArray())
        .name("histogram")
        .help("Histogram without labels")
        .create()
    private val histogramWithLabels = Histogram.build()
        .buckets(*(1..20).map { it.toDouble() }.toDoubleArray())
        .name("histogram_with_labels")
        .help("Histogram with labels")
        .labelNames("label_1", "label_2")
        .create()

    @Benchmark
    @Threads(1)
    open fun histogramWithoutLabels() = cycle {
        histogram.observe(randoms[it])
    }

    @Benchmark
    @Threads(1)
    open fun histogramWithLabels() = cycle {
        histogramWithLabels.labels("test_1", "test_2").observe(randoms[it])
    }
}