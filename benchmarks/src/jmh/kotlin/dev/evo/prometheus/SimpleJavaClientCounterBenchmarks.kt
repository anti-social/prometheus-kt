package dev.evo.prometheus

import io.prometheus.client.Counter

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads

@State(Scope.Benchmark)
open class SimpleJavaClientCounterBenchmarks {
    private val simpleCounter = Counter.build()
        .name("counter")
        .help("Counter without labels")
        .create()
    private val counterWithLabels = Counter.build()
        .name("counter")
        .help("Counter with labels")
        .labelNames("label_1", "label_2")
        .create()

    @Benchmark
    @Threads(1)
    open fun counterWithoutLabels() = cycle {
        simpleCounter.inc()
    }

    @Benchmark
    @Threads(1)
    open fun counterWithLabels() = cycle {
        counterWithLabels.labels("test_1", "test_2").inc()
    }

//    @Benchmark
//    @Threads(4)
//    open fun counterWithoutLabels_4_threads() = cycle {
//        simpleCounter.inc()
//    }
//
//    @Benchmark
//    @Threads(4)
//    open fun counterWithLabels_4_threads() = cycle {
//        counterWithLabels.labels("test_1", "test_2").inc()
//    }
}