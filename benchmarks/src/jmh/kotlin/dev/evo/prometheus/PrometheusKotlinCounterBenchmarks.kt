package dev.evo.prometheus

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads

open class PrometheusKotlinCounterBenchmarks {
    class Labels : LabelSet() {
        val label1 by label("label_1")
        val label2 by label("label_2")
    }

    @State(Scope.Benchmark)
    open class Metrics : PrometheusMetrics() {
        val counterWithoutLabels by counter("counter")
        val counterWithLabels by counter("counter_with_labels") { Labels() }

        val longCounterWithoutLabels by counterLong("long_counter")
        val longCounterWithLabels by counterLong("long_counter_with_labels") { Labels() }
    }

    @Benchmark
    @Threads(1)
    open fun counterWithoutLabels(metrics: Metrics) = cycle {
        metrics.counterWithoutLabels.inc()
    }

    @Benchmark
    @Threads(1)
    open fun counterWithLabels(metrics: Metrics) = cycle {
        metrics.counterWithLabels.inc()
    }

    @Benchmark
    @Threads(1)
    open fun longCounterWithoutLabels(metrics: Metrics) = cycle {
        metrics.longCounterWithoutLabels.inc()
    }

    @Benchmark
    @Threads(1)
    open fun longCounterWithLabels(metrics: Metrics) = cycle {
        metrics.longCounterWithLabels.inc()
    }
}