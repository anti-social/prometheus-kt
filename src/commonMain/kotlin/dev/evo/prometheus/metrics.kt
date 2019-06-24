package dev.evo.prometheus

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlin.math.pow

typealias LabelsSetter<L> = L.() -> Unit

abstract class Metric<L: LabelSet>(
        protected val metrics: PrometheusMetrics,
        val name: String,
        val help: String?,
        private val labelsFactory: (() -> L)?
) {
    abstract val type: String

    companion object {
        private val SUFFIXES: List<String> = emptyList()
    }

    internal open fun getSamleNames(): List<String> {
        return getSampleNamesForSuffixes(SUFFIXES)
    }

    protected fun getSampleNamesForSuffixes(suffixes: List<String>): List<String> {
        val sampleNames = mutableListOf(name)
        suffixes.forEach { suffix ->
            sampleNames.add(suffix.withPrefix(name))
        }
        return sampleNames
    }

    protected fun constructLabels(labelsSetter: LabelsSetter<L>?): LabelSet {
        return if (labelsFactory != null && labelsSetter != null) {
            labelsFactory.invoke().apply { labelsSetter() }
        } else {
            LabelSet.EMPTY
        }
    }
}

class Gauge<L: LabelSet>(
        metrics: PrometheusMetrics,
        name: String,
        help: String?,
        labelsFactory: (() -> L)?
) : Metric<L>(metrics, name, help, labelsFactory) {
    override val type = "gauge"

    suspend fun inc(labelsSetter: LabelsSetter<L>? = null) = add(1.0, labelsSetter)

    suspend fun dec(labelsSetter: LabelsSetter<L>? = null) = add(-1.0, labelsSetter)

    suspend fun <R> incAndDec(labelsSetter: LabelsSetter<L>? = null, block: suspend () -> R): R {
        val labels = constructLabels(labelsSetter)
        add(1.0, labels)
        try {
            return block()
        } finally {
            add(-1.0, labels)
        }
    }

    suspend fun add(value: Double, labelsSetter: LabelsSetter<L>? = null) = add(value, constructLabels(labelsSetter))

    private suspend fun add(value: Double, labels: LabelSet) {
        metrics.getMetricValue(MetricKey(name, labels), MetricValue::Gauge)
            .add(value)
    }

    suspend fun set(value: Double, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        metrics.getMetricValue(MetricKey(name, labels), MetricValue::Gauge)
                .set(value)
    }
}

class GaugeLong<L: LabelSet>(
        metrics: PrometheusMetrics,
        name: String,
        help: String?,
        labelsFactory: (() -> L)?
) : Metric<L>(metrics, name, help, labelsFactory) {
    override val type = "gauge"

    suspend fun inc(labelsSetter: LabelsSetter<L>? = null) = add(1L, labelsSetter)

    suspend fun dec(labelsSetter: LabelsSetter<L>? = null) = add(-1L, labelsSetter)

    suspend fun <R> incAndDec(labelsSetter: LabelsSetter<L>? = null, block: suspend () -> R): R {
        val labels = constructLabels(labelsSetter)
        add(1L, labels)
        try {
            return block()
        } finally {
            add(-1L, labels)
        }
    }

    suspend fun add(value: Long, labelsSetter: LabelsSetter<L>? = null) = add(value, constructLabels(labelsSetter))

    suspend fun add(value: Long, labels: LabelSet) {
        metrics.getMetricValue(MetricKey(name, labels), MetricValue::GaugeLong)
            .add(value)
    }

    suspend fun set(value: Long, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        metrics.getMetricValue(MetricKey(name, labels), MetricValue::GaugeLong)
                .set(value)
    }
}

class Counter<L: LabelSet>(
        metrics: PrometheusMetrics,
        name: String,
        help: String?,
        labelsFactory: (() -> L)?
) : Metric<L>(metrics, name, help, labelsFactory) {
    override val type = "counter"

    suspend fun inc(labelsSetter: LabelsSetter<L>? = null) = add(1.0, labelsSetter)

    suspend fun add(value: Double, labelsSetter: LabelsSetter<L>?) {
        if (value < 0.0) {
            throw IllegalArgumentException("Counter cannot be decreased: $value")
        }
        val labels = constructLabels(labelsSetter)
        metrics.getMetricValue(MetricKey(name, labels), MetricValue::Counter)
                .add(value)
    }
}

class CounterLong<L: LabelSet>(
        metrics: PrometheusMetrics,
        name: String,
        help: String?,
        labelsFactory: (() -> L)?
) : Metric<L>(metrics, name, help, labelsFactory) {
    override val type = "counter"

    suspend fun inc(labelsSetter: LabelsSetter<L>? = null) = add(1L, labelsSetter)

    suspend fun add(value: Long, labelsSetter: LabelsSetter<L>?) {
        if (value < 0L) {
            throw IllegalArgumentException("Counter cannot be decreased: $value")
        }
        val labels = constructLabels(labelsSetter)
        metrics.getMetricValue(MetricKey(name, labels), MetricValue::CounterLong)
                .add(value)
    }
}

class HistogramLabelSet(le: String) : LabelSet() {
    private var le by label("le")

    init {
        this.le = le
    }
}

class Histogram<L: LabelSet>(
        metrics: PrometheusMetrics,
        name: String,
        help: String?,
        labelsFactory: (() -> L)?,
        buckets: List<Double>
) : Metric<L>(metrics, name, help, labelsFactory) {
    override val type = "histogram"

    companion object {
        private val SUFFIXES = listOf("bucket", "count", "sum")
    }

    override fun getSamleNames() = getSampleNamesForSuffixes(SUFFIXES)

    private val buckets = buckets.let {
        if (it.isEmpty()) {
            throw IllegalArgumentException("Buckets must contain at least one value")
        }
        val sortedBuckets = it.sorted().toMutableList()
        if (sortedBuckets.last() < Double.POSITIVE_INFINITY) {
            sortedBuckets.add(Double.POSITIVE_INFINITY)
        }
        sortedBuckets.toDoubleArray()
    }

    suspend fun observe(value: Double, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        val bucketIx = findBucketIx(value)
        metrics.getMetricValue(MetricKey(name, labels)) { MetricValue.Histogram(buckets) }
                .observe(bucketIx, value)
    }

    suspend fun measureTime(labelsSetter: LabelsSetter<L>? = null, block: suspend () -> Unit) {
        val t = measureTimeMillis {
            block()
        }
        observe(t, labelsSetter)
    }

    private fun findBucketIx(value: Double): Int {
        var lowerIx = 0
        var upperIx = buckets.size - 1
        while (true) {
            if (upperIx - lowerIx <= 1) {
                val lowerValue = buckets[lowerIx]
                val upperValue = buckets[upperIx]
                return if (value > lowerValue && value <= upperValue) {
                    upperIx
                } else {
                    lowerIx
                }
            }
            val midIx = (upperIx + lowerIx + 1) / 2
            val bucketValue = buckets[midIx]
            when {
                bucketValue == value -> return midIx
                bucketValue < value -> lowerIx = midIx
                bucketValue > value -> upperIx = midIx
            }
        }
    }
}

class SimpleSummary<L: LabelSet>(
        metrics: PrometheusMetrics,
        name: String,
        help: String?,
        labelsFactory: (() -> L)?
) : Metric<L>(metrics, name, help, labelsFactory) {
    override val type = "summary"

    companion object {
        private val SUFFIXES = listOf("count", "sum")
    }

    override fun getSamleNames() = getSampleNamesForSuffixes(SUFFIXES)

    suspend fun observe(value: Double, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        metrics.getMetricValue(MetricKey(name, labels), MetricValue::SimpleSummary)
                .observe(value)
    }

    suspend fun measureTime(labelsSetter: LabelsSetter<L>? = null, block: suspend () -> Unit) {
        val t = measureTimeMillis {
            block()
        }
        observe(t, labelsSetter)
    }
}

abstract class LabelSet {
    private val _labels = HashMap<String, String>()
    internal val labels: Map<String, String> = _labels

    object EMPTY : LabelSet()

    class LabelDelegate(private val name: String?) {
        operator fun getValue(thisRef: LabelSet, prop: KProperty<*>): String? {
            return thisRef._labels[name ?: prop.name]
        }

        operator fun setValue(thisRef: LabelSet, prop: KProperty<*>, value: String?) {
            if (value == null) {
                thisRef._labels.remove(name ?: prop.name)
            } else {
                thisRef._labels[name ?: prop.name] = value
            }
        }
    }
    fun label(name: String? = null) = LabelDelegate(name)

    override fun equals(other: Any?): Boolean {
        if (other !is LabelSet) return false
        return _labels == other._labels
    }

    override fun hashCode(): Int {
        return _labels.hashCode()
    }

    override fun toString(): String {
        return toString(null)
    }

    fun toString(additionalLabels: LabelSet?): String {
        if (_labels.isEmpty() && additionalLabels?._labels.isNullOrEmpty()) {
            return ""
        }
        return sequenceOf(
                _labels.asSequence(),
                additionalLabels?._labels?.asSequence() ?: emptySequence()
        )
                .flatten()
                .joinToString(separator = ",", prefix = "{", postfix = "}") {
                    "${it.key}=\"${it.value}\""
                }
    }
}

data class MetricKey(val name: String, val labels: LabelSet)
sealed class MetricValue {
    abstract val numSamples: Int
    abstract fun produceSamples(name: String, labels: LabelSet, samples: Samples)

    class Counter: MetricValue() {
        private val value = atomic(0.0.toBits())

        override val numSamples: Int = 1

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

abstract class PrometheusMetrics {
    private val registry = mutableMapOf<String, Metric<*>>()
    private val sampleNames = mutableSetOf<String>()
    private val values = MetricValuesContainer()

    companion object {
        /**
         * Generates logarithm scale. It is useful for generating histogram buckets.
         *
         * For example:
         * logScale(0, 1) will generate next sequence:
         * listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0)
         */
        fun logScale(startOrder: Int, endOrder: Int): List<Double> {
            if (startOrder > endOrder) {
                throw IllegalArgumentException(
                    "[startOrder=$startOrder] must be less than or equal [endOrder=$endOrder]"
                )
            }
            val scale = ArrayList<Double>((endOrder - startOrder) * 9 + 1)
            (startOrder..endOrder).forEach { order ->
                val factor = 10.0.pow(order)
                (1..9).forEach { v ->
                    scale.add(v * factor)
                }
            }
            scale.add(10.0.pow(endOrder + 1))
            return scale
        }
    }

    class MetricDelegateProvider<M: Metric<L>, L: LabelSet>(
            private val name: String,
            private val metricFactory: (PrometheusMetrics) -> M
    ) {
        operator fun provideDelegate(
                thisRef: PrometheusMetrics,
                prop: KProperty<*>
        ) = object : ReadOnlyProperty<PrometheusMetrics, M> {
            private val metric = metricFactory(thisRef).also { m ->
                thisRef.checkMetricSampleNames(m, prop.name)
                thisRef.registry[name] = m
            }

            override fun getValue(thisRef: PrometheusMetrics, property: KProperty<*>) = metric
        }
    }

    fun <L: LabelSet> gauge(
            name: String, help: String? = null, labelsFactory: () -> L
    ): MetricDelegateProvider<Gauge<L>, L>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            Gauge(promMetrics, name, help, labelsFactory)
        }
    }
    fun gauge(
            name: String, help: String? = null
    ): MetricDelegateProvider<Gauge<LabelSet.EMPTY>, LabelSet.EMPTY>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            Gauge(promMetrics, name, help, null)
        }
    }
    fun <L: LabelSet> gaugeLong(
            name: String, help: String? = null, labelsFactory: () -> L
    ): MetricDelegateProvider<GaugeLong<L>, L>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            GaugeLong(promMetrics, name, help, labelsFactory)
        }
    }
    fun gaugeLong(
            name: String, help: String? = null
    ): MetricDelegateProvider<GaugeLong<LabelSet.EMPTY>, LabelSet.EMPTY>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            GaugeLong(promMetrics, name, help, null)
        }
    }

    fun <L: LabelSet> counter(
            name: String, help: String? = null, labelsFactory: () -> L
    ): MetricDelegateProvider<Counter<L>, L>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            Counter(promMetrics, name, help, labelsFactory)
        }
    }
    fun counter(
            name: String, help: String? = null
    ): MetricDelegateProvider<Counter<LabelSet.EMPTY>, LabelSet.EMPTY>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            Counter(promMetrics, name, help, null)
        }
    }
    fun <L: LabelSet> counterLong(
            name: String, help: String? = null, labelsFactory: () -> L
    ): MetricDelegateProvider<CounterLong<L>, L>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            CounterLong(promMetrics, name, help, labelsFactory)
        }
    }
    fun counterLong(
            name: String, help: String? = null
    ): MetricDelegateProvider<CounterLong<LabelSet.EMPTY>, LabelSet.EMPTY>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            CounterLong(promMetrics, name, help, null)
        }
    }

    fun <L: LabelSet> simpleSummary(
            name: String, help: String? = null, labelsFactory: () -> L
    ): MetricDelegateProvider<SimpleSummary<L>, L>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            SimpleSummary(promMetrics, name, help, labelsFactory)
        }
    }
    fun simpleSummary(
            name: String, help: String? = null
    ): MetricDelegateProvider<SimpleSummary<LabelSet.EMPTY>, LabelSet.EMPTY>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            SimpleSummary(promMetrics, name, help, null)
        }
    }

    fun <L: LabelSet> histogram(
            name: String, buckets: List<Double>, help: String? = null, labelsFactory: () -> L
    ): MetricDelegateProvider<Histogram<L>, L>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            Histogram(promMetrics, name, help, labelsFactory, buckets)
        }
    }
    fun histogram(
            name: String, buckets: List<Double>, help: String? = null
    ): MetricDelegateProvider<Histogram<LabelSet.EMPTY>, LabelSet.EMPTY>
    {
        return MetricDelegateProvider(name) { promMetrics ->
            Histogram(promMetrics, name, help, null, buckets)
        }
    }

    private val submetrics = HashMap<String, SubMetrics>()

    private class SubMetrics(val prefix: String, val metrics: PrometheusMetrics)

    class SubmetricsDeletageProvider<M: PrometheusMetrics>(
            private val metrics: M,
            private val prefix: String?
    ) {
        operator fun provideDelegate(thisRef: PrometheusMetrics, prop: KProperty<*>): ReadOnlyProperty<PrometheusMetrics, M> {
            val sm = SubMetrics(prefix ?: "", metrics)
            thisRef.checkSubMetricsSampleNames(sm, prop.name)
            thisRef.submetrics[prop.name] = sm

            return object : ReadOnlyProperty<PrometheusMetrics, M> {
                override fun getValue(thisRef: PrometheusMetrics, property: KProperty<*>): M {
                    return metrics
                }
            }
        }
    }
    fun <M: PrometheusMetrics> submetrics(submetrics: M): SubmetricsDeletageProvider<M> {
        return SubmetricsDeletageProvider(submetrics, null)
    }
    fun <M: PrometheusMetrics> submetrics(prefix: String, submetrics: M): SubmetricsDeletageProvider<M> {
        return SubmetricsDeletageProvider(submetrics, prefix)
    }

    private fun checkMetricSampleNames(m: Metric<*>, propName: String) {
        m.getSamleNames().forEach {
            if (it in sampleNames) {
                throw IllegalArgumentException(
                    "[$it] sample has already been added by [$propName] metric"
                )
            } else {
                sampleNames.add(it)
            }
        }
    }

    private fun checkSubMetricsSampleNames(sm: SubMetrics, propName: String) {
        sm.metrics.sampleNames.forEach {
            val sampleName = it.withPrefix(sm.prefix)
            if (sampleName in sampleNames) {
                throw IllegalArgumentException(
                    "[$sampleName] sample has already been added by [$propName] sub-metrics"
                )
            } else {
                sampleNames.add(sampleName)
            }
        }
    }

    internal suspend inline fun <reified M: MetricValue> getMetricValue(
            key: MetricKey,
            noinline initialValue: () -> M
    ): M {
        return values.getOrPut(key, initialValue) as M
    }

    open suspend fun collect() {
        submetrics.values.forEach { it.metrics.collect() }
    }

    suspend fun dump(): HashMap<String, Samples> {
        return HashMap<String, Samples>(values.estimatedSamplesCount)
                .also { dumpTo(it, "") }
    }

    private suspend fun dumpTo(result: HashMap<String, Samples>, prefix: String) {
        values.forEach { (key, value) ->
            val sampleName = key.name.withPrefix(prefix)
            val metric = registry[key.name] ?: return@forEach
            val samples = result.getOrPut(sampleName) { Samples(sampleName, metric.type, metric.help) }
            value.produceSamples(sampleName, key.labels, samples)
        }

        submetrics.values.forEach {
            it.metrics.dumpTo(result, it.prefix.withPrefix(prefix))
        }
    }
}

fun writeSamples(result: HashMap<String, Samples>, output: Appendable) {
    for ((_, samples) in result) {
        if (samples.help != null) {
            output.append("# HELP ${samples.name} ${samples.help}\n")
        }
        output.append("# TYPE ${samples.name} ${samples.type}\n")
        for (sample in samples) {
            val renderedLabels = sample.labels.toString(sample.additionalLabels)
            output.append("${sample.name}$renderedLabels ${sample.value.toGoString()}\n")
        }
    }
}

private fun String.withPrefix(prefix: String) = if (prefix.isEmpty()) {
    this
} else {
    "${prefix}_$this"
}

private fun Double.toGoString(): String {
    return when {
        this == Double.NEGATIVE_INFINITY -> "-Inf"
        this == Double.POSITIVE_INFINITY -> "+Inf"
        this.isNaN() -> "NaN"
        else -> this.toString()
    }
}
