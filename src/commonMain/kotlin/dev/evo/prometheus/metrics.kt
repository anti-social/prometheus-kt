package dev.evo.prometheus

import dev.evo.prometheus.util.MetricValuesContainer
import dev.evo.prometheus.util.measureTimeMillis

import kotlin.math.abs
import kotlin.math.pow
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

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

    protected fun constructLabels(labelsSetter: LabelsSetter<L>?): L? {
        return if (labelsFactory != null && labelsSetter != null) {
            labelsFactory.invoke().apply { labelsSetter() }
        } else {
            null
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

    suspend fun getMetricValue(labels: L? = null): MetricValue.Gauge? {
        return metrics.find<MetricValue.Gauge>(
            MetricKey(name, labels ?: LabelSet.EMPTY)
        )
    }

    suspend fun getOrCreateMetricValue(labels: L? = null): MetricValue.Gauge {
        return metrics.getOrCreate<MetricValue.Gauge>(
            MetricKey(name, labels ?: LabelSet.EMPTY), MetricValue::Gauge
        )
    }

    suspend fun getMetricData(labels: L? = null): Double? {
        return getMetricValue(labels)?.get()
    }

    @Deprecated(
        message = "Replaced with 'getMetricData'",
        replaceWith = ReplaceWith("getMetricData"),
    )
    suspend fun get(labels: L? = null): Double? = getMetricData(labels)

    suspend fun inc(labelsSetter: LabelsSetter<L>? = null) = add(1.0, labelsSetter)

    suspend fun dec(labelsSetter: LabelsSetter<L>? = null) = add(-1.0, labelsSetter)

    suspend fun <R> incAndDec(labelsSetter: LabelsSetter<L>? = null, block: suspend () -> R): R {
        val labels = constructLabels(labelsSetter)
        val metricValue = getOrCreateMetricValue(labels)
        metricValue.add(1.0)
        try {
            return block()
        } finally {
            metricValue.add(-1.0)
        }
    }

    suspend fun add(value: Double, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        getOrCreateMetricValue(labels).add(value)
    }

    suspend fun set(value: Double, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        getOrCreateMetricValue(labels).set(value)
    }
}

class GaugeLong<L: LabelSet>(
        metrics: PrometheusMetrics,
        name: String,
        help: String?,
        labelsFactory: (() -> L)?
) : Metric<L>(metrics, name, help, labelsFactory) {
    override val type = "gauge"

    suspend fun getMetricValue(labels: L? = null): MetricValue.GaugeLong? {
        return metrics.find<MetricValue.GaugeLong>(
            MetricKey(name, labels ?: LabelSet.EMPTY)
        )
    }

    suspend fun getOrCreateMetricValue(labels: L? = null): MetricValue.GaugeLong {
        return metrics.getOrCreate<MetricValue.GaugeLong>(
            MetricKey(name, labels ?: LabelSet.EMPTY), MetricValue::GaugeLong
        )
    }

    suspend fun getMetricData(labels: L? = null): Long? {
        return getMetricValue(labels)?.get()
    }

    @Deprecated(
        message = "Replaced with 'getMetricData'",
        replaceWith = ReplaceWith("getMetricData"),
    )
    suspend fun get(labels: L? = null): Long? = getMetricData(labels)

    suspend fun inc(labelsSetter: LabelsSetter<L>? = null) = add(1L, labelsSetter)

    suspend fun dec(labelsSetter: LabelsSetter<L>? = null) = add(-1L, labelsSetter)

    suspend fun <R> incAndDec(labelsSetter: LabelsSetter<L>? = null, block: suspend () -> R): R {
        val labels = constructLabels(labelsSetter)
        val metricValue = getOrCreateMetricValue(labels)
        metricValue.add(1L)
        try {
            return block()
        } finally {
            metricValue.add(-1L)
        }
    }

    suspend fun add(value: Long, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        getOrCreateMetricValue(labels).add(value)
    }

    suspend fun set(value: Long, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        getOrCreateMetricValue(labels).set(value)
    }
}

class Counter<L: LabelSet>(
        metrics: PrometheusMetrics,
        name: String,
        help: String?,
        labelsFactory: (() -> L)?
) : Metric<L>(metrics, name, help, labelsFactory) {
    override val type = "counter"

    suspend fun getMetricValue(labels: L? = null): MetricValue.Counter? {
        return metrics.find<MetricValue.Counter>(
            MetricKey(name, labels ?: LabelSet.EMPTY)
        )
    }

    suspend fun getOrCreateMetricValue(labels: L? = null): MetricValue.Counter {
        return metrics.getOrCreate<MetricValue.Counter>(
            MetricKey(name, labels ?: LabelSet.EMPTY), MetricValue::Counter
        )
    }

    suspend fun getMetricData(labels: L? = null): Double? {
        return getMetricValue(labels)?.get()
    }

    @Deprecated(
        message = "Replaced with 'getMetricData'",
        replaceWith = ReplaceWith("getMetricData"),
    )
    suspend fun get(labels: L? = null): Double? = getMetricData(labels)

    suspend fun inc(labelsSetter: LabelsSetter<L>? = null) = add(1.0, labelsSetter)

    suspend fun add(value: Double, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        getOrCreateMetricValue(labels).add(value)
    }
}

class CounterLong<L: LabelSet>(
        metrics: PrometheusMetrics,
        name: String,
        help: String?,
        labelsFactory: (() -> L)?
) : Metric<L>(metrics, name, help, labelsFactory) {
    override val type = "counter"

    suspend fun getOrCreateMetricValue(labels: L? = null): MetricValue.CounterLong {
        return metrics.getOrCreate<MetricValue.CounterLong>(
            MetricKey(name, labels ?: LabelSet.EMPTY), MetricValue::CounterLong
        )
    }

    suspend fun getMetricValue(labels: L? = null): MetricValue.CounterLong? {
        return metrics.find<MetricValue.CounterLong>(
            MetricKey(name, labels ?: LabelSet.EMPTY)
        )
    }

    suspend fun getMetricData(labels: L? = null): Long? {
        return getMetricValue(labels)?.get()
    }

    @Deprecated(
        message = "Replaced with 'getMetricData'",
        replaceWith = ReplaceWith("getMetricData"),
    )
    suspend fun get(labels: L? = null): Long? = getMetricData(labels)

    suspend fun inc(labelsSetter: LabelsSetter<L>? = null) = add(1L, labelsSetter)

    suspend fun add(value: Long, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        getOrCreateMetricValue(labels).add(value)
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

    suspend fun getMetricValue(labels: L? = null): MetricValue.Histogram? {
        return metrics.find<MetricValue.Histogram>(
            MetricKey(name, labels ?: LabelSet.EMPTY)
        )
    }

    suspend fun getOrCreateMetricValue(labels: L? = null): MetricValue.Histogram {
        return metrics.getOrCreate<MetricValue.Histogram>(
            MetricKey(name, labels ?: LabelSet.EMPTY)
        ) {
            MetricValue.Histogram(buckets)
        }
    }

    suspend fun getMetricData(labels: L? = null): MetricValue.Histogram.Data? {
        return getMetricValue(labels)?.get()
    }

    @Deprecated(
        message = "Replaced with 'getMetricData'",
        replaceWith = ReplaceWith("getMetricData"),
    )
    suspend fun get(labels: L? = null): MetricValue.Histogram.Data? = getMetricData(labels)

    suspend fun observe(value: Double, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        val metricValue = getOrCreateMetricValue(labels)
        val bucketIx = metricValue.findBucketIx(value)
        metricValue.observe(bucketIx, value)
    }

    suspend fun observe(
        values: DoubleArray,
        offset: Int,
        size: Int,
        labelsSetter: LabelsSetter<L>? = null,
    ) {
        val labels = constructLabels(labelsSetter)
        val metric = getOrCreateMetricValue(labels)
        var ix = offset
        while (ix < offset + size) {
            val v = values[ix]
            val bucketIx = metric.findBucketIx(v)
            metric.observe(bucketIx, v)
            ix++
        }
    }

    suspend fun measureTime(labelsSetter: LabelsSetter<L>? = null, block: suspend () -> Unit) {
        var exc: Throwable? = null
        val v = measureTimeMillis {
            try {
                block()
            } catch (t: Throwable) {
                exc = t
            }
        }
        observe(v, labelsSetter)
        exc?.let { e ->
            throw e
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

    suspend fun getMetricValue(labels: L? = null): MetricValue.SimpleSummary? {
        return metrics.find<MetricValue.SimpleSummary>(
            MetricKey(name, labels ?: LabelSet.EMPTY)
        )
    }

    suspend fun getOrCreateMetricValue(labels: L? = null): MetricValue.SimpleSummary {
        return metrics.getOrCreate<MetricValue.SimpleSummary>(
            MetricKey(name, labels ?: LabelSet.EMPTY), MetricValue::SimpleSummary
        )
    }

    suspend fun getMetricData(labels: L? = null): MetricValue.SimpleSummary.Data? {
        return getMetricValue(labels)?.get()
    }

    @Deprecated(
        message = "Replaced with 'getMetricData'",
        replaceWith = ReplaceWith("getMetricData"),
    )
    suspend fun get(labels: L? = null): MetricValue.SimpleSummary.Data? = getMetricData(labels)

    suspend fun observe(value: Double, labelsSetter: LabelsSetter<L>? = null) {
        val labels = constructLabels(labelsSetter)
        getOrCreateMetricValue(labels).observe(value)
    }

    suspend fun measureTime(labelsSetter: LabelsSetter<L>? = null, block: suspend () -> Unit) {
        var exc: Throwable? = null
        val v = measureTimeMillis {
            try {
                block()
            } catch (t: Throwable) {
                exc = t
            }
        }
        observe(v, labelsSetter)
        exc?.let { e ->
            throw e
        }
    }
}

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
                val factor = 10.0.pow(abs(order))
                (1..9).forEach { v ->
                    scale.add(if (order >= 0) v * factor else v / factor)
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
            name: String, help: String? = null, labelsFactory: (() -> L)?
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
        return gauge(name, help, null)
    }
    fun <L: LabelSet> gaugeLong(
            name: String, help: String? = null, labelsFactory: (() -> L)?
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
        return gaugeLong(name, help, null)
    }

    fun <L: LabelSet> counter(
            name: String, help: String? = null, labelsFactory: (() -> L)?
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
        return counter(name, help, null)
    }
    fun <L: LabelSet> counterLong(
            name: String, help: String? = null, labelsFactory: (() -> L)?
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
        return counterLong(name, help, null)
    }

    fun <L: LabelSet> simpleSummary(
            name: String, help: String? = null, labelsFactory: (() -> L)?
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
        return simpleSummary(name, help, null)
    }

    fun <L: LabelSet> histogram(
            name: String, buckets: List<Double>, help: String? = null, labelsFactory: (() -> L)?
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
        return histogram(name, buckets, help, null)
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

    internal suspend inline fun <reified M: MetricValue> getOrCreate(
            key: MetricKey,
            noinline initialValue: () -> M
    ): M {
        return values.getOrPut(key, initialValue) as M
    }

    internal suspend inline fun <M: MetricValue> find(
            key: MetricKey,
    ): M? {
        @Suppress("UNCHECKED_CAST")
        return values.get(key) as M?
    }

    open suspend fun collect() {
        submetrics.values.forEach { it.metrics.collect() }
    }

    suspend fun dump(): HashMap<String, Samples> {
        return HashMap<String, Samples>(values.estimatedSamplesCount)
                .also { dumpTo(it, "") }
    }

    private suspend fun dumpTo(result: HashMap<String, Samples>, prefix: String) {
        values.forEach { key, value ->
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

internal fun Double.toGoString(): String {
    return when {
        this == Double.NEGATIVE_INFINITY -> "-Inf"
        this == Double.POSITIVE_INFINITY -> "+Inf"
        this.isNaN() -> "NaN"
        else -> this.toString()
    }
}
