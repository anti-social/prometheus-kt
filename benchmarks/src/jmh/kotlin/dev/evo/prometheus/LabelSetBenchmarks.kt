package dev.evo.prometheus

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.infra.Blackhole

open class LabelSetBenchmarks {
    class Labels : LabelSet() {
        var label1 by label("label_1")
        var label2 by label("label_2")
    }

    class BaseLineLabels {
        var label1: String? = null
        var label2: String? = null
    }

    @Benchmark
    @Threads(1)
    open fun baseLineLabels(blackhole: Blackhole) {
        cycle {
            blackhole.consume(BaseLineLabels().apply {
                label1 = "test 1"
                label2 = "test 2"
            })
        }
    }

    @Benchmark
    @Threads(1)
    open fun labelSet(blackhole: Blackhole) {
        cycle {
            blackhole.consume(Labels().apply {
                label1 = "test 1"
                label2 = "test 2"
            })
        }
    }
}
