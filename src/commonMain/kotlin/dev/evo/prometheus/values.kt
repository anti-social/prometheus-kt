package dev.evo.prometheus

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update

data class Sample(
    val name: String,
    val value: Double,
    val labels: LabelSet,
    val additionalLabels: LabelSet? = null
)

class Samples(
    val name: String,
    val type: String,
    val help: String?,
    private val samples: MutableList<Sample> = mutableListOf()
) : MutableList<Sample> by samples

data class MetricKey(val name: String, val labels: LabelSet)

sealed class MetricValue {
    abstract val numSamples: Int
    abstract fun produceSamples(name: String, labels: LabelSet, samples: Samples)

    class Counter: MetricValue() {
        private val value = atomic(0.0.toBits())

        override val numSamples: Int = 1

        fun get(): Double {
            return Double.fromBits(value.value)
        }

        fun add(v: Double) {
            value.update { old ->
                (Double.fromBits(old) + v).toBits()
            }
        }

        override fun produceSamples(name: String, labels: LabelSet, samples: Samples) {
            samples.add(
                Sample(name, Double.fromBits(value.value), labels)
            )
        }
    }

    class CounterLong: MetricValue() {
        private val value = atomic(0L)

        override val numSamples: Int = 1

        fun get(): Long {
            return value.value
        }

        fun add(v: Long) {
            value.update { old -> old + v }
        }

        override fun produceSamples(name: String, labels: LabelSet, samples: Samples) {
            samples.add(
                Sample(name, value.value.toDouble(), labels)
            )
        }
    }

    class Gauge: MetricValue() {
        private val value = atomic(0.0.toBits())

        override val numSamples: Int = 1

        fun get(): Double {
            return Double.fromBits(value.value)
        }

        fun set(v: Double) {
            value.update { v.toBits() }
        }

        fun add(v: Double) {
            value.update { old ->
                (Double.fromBits(old) + v).toBits()
            }
        }

        override fun produceSamples(name: String, labels: LabelSet, samples: Samples) {
            samples.add(
                Sample(name, Double.fromBits(value.value), labels)
            )
        }
    }

    class GaugeLong: MetricValue() {
        private val value = atomic(0L)

        override val numSamples: Int = 1

        fun get(): Long {
            return value.value
        }

        fun set(v: Long) {
            value.update { v }
        }

        fun add(v: Long) {
            value.update { old -> old + v }
        }

        override fun produceSamples(name: String, labels: LabelSet, samples: Samples) {
            samples.add(
                Sample(name, value.value.toDouble(), labels)
            )
        }
    }

    class SimpleSummary: MetricValue() {
        private val count = atomic(0L)
        private val sum = atomic(0.0.toBits())

        override val numSamples: Int = 2

        data class Data(
            val count: Long,
            val sum: Double
        )

        fun get(): Data {
            // It is not an atomic operation but these are just metrics
            return Data(
                count.value,
                Double.fromBits(sum.value)
            )
        }

        fun observe(v: Double) {
            // It is not an atomic operation but these are just metrics
            count.incrementAndGet()
            sum.update { old ->
                (Double.fromBits(old) + v).toBits()
            }
        }

        override fun produceSamples(name: String, labels: LabelSet, samples: Samples) {
            samples.add(
                Sample("${name}_count", count.value.toDouble(), labels)
            )
            samples.add(
                Sample("${name}_sum", Double.fromBits(sum.value), labels)
            )
        }
    }

    class Histogram(private val buckets: DoubleArray): MetricValue() {
        private val histogram = Array(buckets.size) {
            atomic(0L)
        }
        private val count = atomic(0L)
        private val sum = atomic(0.0.toBits())

        override val numSamples: Int = 2 + buckets.size

        data class Data(
            val count: Long,
            val sum: Double,
            val histogram: Array<Long>
        )

        fun get(): Data {
            return Data(
                count.value,
                Double.fromBits(sum.value),
                Array(buckets.size) { bucketIx ->
                    histogram[bucketIx].value
                }
            )
        }

        fun observe(bucketIx: Int, v: Double) {
            histogram[bucketIx].incrementAndGet()
            count.incrementAndGet()
            sum.update { old ->
                (Double.fromBits(old) + v).toBits()
            }
        }

        override fun produceSamples(name: String, labels: LabelSet, samples: Samples) {
            var ix = 0
            var cumulativeCount = 0L
            while (ix < buckets.size) {
                val addLabels = HistogramLabelSet(le = buckets[ix].toGoString())
                cumulativeCount += histogram[ix].value
                samples.add(
                    Sample("${name}_bucket",
                        cumulativeCount.toDouble(), labels, addLabels)
                )
                ix++
            }
            samples.add(
                Sample("${name}_count", count.value.toDouble(), labels)
            )
            samples.add(
                Sample("${name}_sum", Double.fromBits(sum.value), labels)
            )
        }
    }
}
