package dev.evo.prometheus

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MetricTests {
    private class KafkaLabels(topic: String? = null, routing: String? = null) : LabelSet() {
        var topic by label("topic")
        var routing by label("routing")

        init {
            if (topic != null) {
                this.topic = topic
            }
            if (routing != null) {
                this.routing = routing
            }
        }
    }

    private class TestMetrics : PrometheusMetrics() {
        val gcCount by counter("gc_count", help = "GC counter")
        val processedMessages by counter("processed_messages") {
            KafkaLabels()
        }
        val cpuUsagePercent by gauge("cpu_usage_percent")
        val cpuUsagePercentLabeled by gauge(
            "cpu_usage_percent_labeled", labelsFactory = ::JustLabel
        )
        val requestsInProcess by gaugeLong("requests_in_process")
        val requestsInProcessLabeled by gaugeLong(
            "requests_in_process_labeled", labelsFactory = ::JustLabel
        )
        val requests by simpleSummary("requests")
        val requestsLabeled by simpleSummary(
            "requests_labeled", labelsFactory = ::JustLabel
        )
        val httpRequests by histogram("http_requests", logScale(0, 0))
        val httpRequestsLabeled by histogram(
            "http_requests_labeled", logScale(0, 0), labelsFactory = ::JustLabel
        )
    }

    private class JustLabel() : LabelSet() {
        var label by label()

        constructor(label: String) : this() {
            this.label = label
        }
    }

    private class Counters : PrometheusMetrics() {
        val simpleCounter by counter("simple_counter")
        val simpleCounterLabeled by counter("simple_counter_labeled") { JustLabel() }
        val longCounter by counterLong("long_counter")
        val longCounterLabeled by counterLong("long_counter_labeled") { JustLabel() }
    }

    private class NestedMetrics : PrometheusMetrics() {
        val nested by submetrics(TestMetrics())
        val prefixed by submetrics("prefixed", TestMetrics())
    }

    private class InvalidHistogram : PrometheusMetrics() {
        val hist by histogram("hist", emptyList())
    }

    private class ClashingMetrics : PrometheusMetrics() {
        val gcCount by counter("gc_count", help = "GC counter")
        val gcTime by gauge("gc_count", help = "GC time")
    }

    private class ClashingNestedMetrics : PrometheusMetrics() {
        val nestedTest by counter("nested_test")
        val nested by submetrics("nested", NestedMetrics())

        class NestedMetrics : PrometheusMetrics() {
            val test by counter("test")
        }
    }

    @Test
    @JsName("logarithmScale")
    fun `logarithm scale`() {
        assertFailsWith<IllegalArgumentException> {
            PrometheusMetrics.logScale(1, 0)
        }
        assertEquals(
            (1..10).map { it.toDouble() }.toList(),
            PrometheusMetrics.logScale(0, 0)
        )
        assertEquals(
            (1..10).map { it.toDouble() }.toList() +
                    (2..10).map { (it * 10).toDouble() },
            PrometheusMetrics.logScale(0, 1)
        )
        assertEquals(
            listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0),
            PrometheusMetrics.logScale(-1, 0)
        )
    }

    @Test
    @JsName("incrementCounter")
    fun `increment counter`() = runTest {
        val metrics = TestMetrics()
        metrics.gcCount.inc()
        assertSamplesShouldMatchOnce(
            metrics.dump(), "gc_count", "counter", "GC counter",
            listOf(
                SampleMatcher("gc_count", 1.0)
            )
        )
    }

    @Test
    @JsName("incrementCounterWithLabels")
    fun `increment counter with labels`() = runTest {
        val metrics = TestMetrics()
        metrics.processedMessages.inc()
        assertSamplesShouldMatchOnce(
            metrics.dump(), "processed_messages", "counter", null,
            listOf(
                SampleMatcher("processed_messages", 1.0)
            )
        )
        metrics.processedMessages.inc {
            topic = "test-topic-1"
        }
        assertSamplesShouldMatchOnce(
            metrics.dump(), "processed_messages", "counter", null,
            listOf(
                SampleMatcher("processed_messages", 1.0),
                SampleMatcher("processed_messages", 1.0, KafkaLabels("test-topic-1"))
            )
        )
        metrics.processedMessages.inc {
            topic = "test-topic-2"
        }
        assertSamplesShouldMatchOnce(
            metrics.dump(), "processed_messages", "counter", null,
            listOf(
                SampleMatcher("processed_messages", 1.0),
                SampleMatcher("processed_messages", 1.0, KafkaLabels("test-topic-1")),
                SampleMatcher("processed_messages", 1.0, KafkaLabels("test-topic-2"))
            )
        )
        metrics.processedMessages.inc {
            topic = "test-topic-2"
            routing = "test-routing-1"
        }
        assertSamplesShouldMatchOnce(
            metrics.dump(), "processed_messages", "counter", null,
            listOf(
                SampleMatcher("processed_messages", 1.0),
                SampleMatcher("processed_messages", 1.0, KafkaLabels("test-topic-2")),
                SampleMatcher("processed_messages", 1.0, KafkaLabels("test-topic-2", "test-routing-1")),
                SampleMatcher("processed_messages", 1.0, KafkaLabels("test-topic-1"))
            )
        )
    }

     @Test
     @JsName("setGauge")
     fun `set gauge`() = runTest {
         val metrics = TestMetrics()

         metrics.cpuUsagePercent.set(59.3)
         assertSamplesShouldMatchOnce(
             metrics.dump(), "cpu_usage_percent", "gauge", null,
             listOf(
                 SampleMatcher("cpu_usage_percent", 59.3)
             )
         )

         metrics.cpuUsagePercentLabeled.set(59.3) {
             label = "just-label"
         }
         assertSamplesShouldMatchOnce(
             metrics.dump(), "cpu_usage_percent_labeled", "gauge", null,
             listOf(
                 SampleMatcher("cpu_usage_percent_labeled", 59.3, JustLabel("just-label"))
             )
         )

         metrics.requestsInProcess.set(2)
         assertSamplesShouldMatchOnce(
             metrics.dump(), "requests_in_process", "gauge", null,
             listOf(
                 SampleMatcher("requests_in_process", 2.0)
             )
         )

         metrics.requestsInProcessLabeled.set(2) {
             label = "another-label"
         }
         assertSamplesShouldMatchOnce(
             metrics.dump(), "requests_in_process_labeled", "gauge", null,
             listOf(
                 SampleMatcher("requests_in_process_labeled", 2.0, JustLabel("another-label"))
             )
         )
     }

     @Test
     @JsName("incrementThenDecrementGauge")
     fun `increment then decrement gauge`() = runTest {
         val metrics = TestMetrics()

         metrics.cpuUsagePercent.set(81.3)
         metrics.cpuUsagePercent.inc()
         assertSamplesShouldMatchOnce(
             metrics.dump(), "cpu_usage_percent", "gauge", null,
             listOf(
                 SampleMatcher("cpu_usage_percent", 82.3)
             )
         )
         metrics.cpuUsagePercent.dec()
         assertSamplesShouldMatchOnce(
             metrics.dump(), "cpu_usage_percent", "gauge", null,
             listOf(
                 SampleMatcher("cpu_usage_percent", 81.3)
             )
         )
         metrics.cpuUsagePercent.incAndDec {
             assertSamplesShouldMatchOnce(
                 metrics.dump(), "cpu_usage_percent", "gauge", null,
                 listOf(
                     SampleMatcher("cpu_usage_percent", 82.3)
                 )
             )
         }
         assertSamplesShouldMatchOnce(
             metrics.dump(), "cpu_usage_percent", "gauge", null,
             listOf(
                 SampleMatcher("cpu_usage_percent", 81.3)
             )
         )

         metrics.requestsInProcess.inc()
         assertSamplesShouldMatchOnce(
             metrics.dump(), "requests_in_process", "gauge", null,
             listOf(
                 SampleMatcher("requests_in_process", 1.0)
             )
         )
         metrics.requestsInProcess.dec()
         assertSamplesShouldMatchOnce(
             metrics.dump(), "requests_in_process", "gauge", null,
             listOf(
                 SampleMatcher("requests_in_process", 0.0)
             )
         )

         metrics.requestsInProcess.incAndDec {
             assertSamplesShouldMatchOnce(
                 metrics.dump(), "requests_in_process", "gauge", null,
                 listOf(
                     SampleMatcher("requests_in_process", 1.0)
                 )
             )
         }

         assertSamplesShouldMatchOnce(
             metrics.dump(), "requests_in_process", "gauge", null,
             listOf(
                 SampleMatcher("requests_in_process", Matcher.Eq(0.0))
             )
         )
     }

    @Test
    fun counters() = runTest {
        val counters = Counters()

        assertFailsWith<IllegalArgumentException> {
            counters.simpleCounter.add(-0.2)
        }
        assertFailsWith<IllegalArgumentException> {
            counters.longCounter.add(-1)
        }

        counters.simpleCounter.inc()
        assertSamplesShouldMatchOnce(
            counters.dump(), "simple_counter", "counter", null,
            listOf(
                SampleMatcher("simple_counter", 1.0)
            )
        )
        counters.simpleCounter.add(2.0)
        assertSamplesShouldMatchOnce(
            counters.dump(), "simple_counter", "counter", null,
            listOf(
                SampleMatcher("simple_counter", 3.0)
            )
        )

        counters.simpleCounterLabeled.inc()
        counters.simpleCounterLabeled.inc { label = "test" }
        assertSamplesShouldMatchOnce(
            counters.dump(), "simple_counter_labeled", "counter", null,
            listOf(
                SampleMatcher("simple_counter_labeled", 1.0),
                SampleMatcher("simple_counter_labeled", 1.0, JustLabel().apply { label = "test" })
            )
        )
        counters.simpleCounterLabeled.add(2.0)
        counters.simpleCounterLabeled.add(3.0) { label = "test"}
        assertSamplesShouldMatchOnce(
            counters.dump(), "simple_counter_labeled", "counter", null,
            listOf(
                SampleMatcher("simple_counter_labeled", 3.0),
                SampleMatcher("simple_counter_labeled", 4.0, JustLabel().apply { label = "test" })
            )
        )

        counters.longCounter.inc()
        assertSamplesShouldMatchOnce(
            counters.dump(), "long_counter", "counter", null,
            listOf(
                SampleMatcher("long_counter", 1.0)
            )
        )
        counters.longCounter.add(2)
        assertSamplesShouldMatchOnce(
            counters.dump(), "long_counter", "counter", null,
            listOf(
                SampleMatcher("long_counter", 3.0)
            )
        )

        counters.longCounterLabeled.inc()
        counters.longCounterLabeled.inc { label = "test" }
        assertSamplesShouldMatchOnce(
            counters.dump(), "long_counter_labeled", "counter", null,
            listOf(
                SampleMatcher("long_counter_labeled", 1.0),
                SampleMatcher("long_counter_labeled", 1.0, JustLabel().apply { label = "test" })
            )
        )
        counters.longCounterLabeled.add(2)
        counters.longCounterLabeled.add(3) { label = "test" }
        assertSamplesShouldMatchOnce(
            counters.dump(), "long_counter_labeled", "counter", null,
            listOf(
                SampleMatcher("long_counter_labeled", 3.0),
                SampleMatcher("long_counter_labeled", 4.0, JustLabel().apply { label = "test" })
            )
        )
    }

    @Test
    @JsName("observeSimpleSummary")
    fun `observe simple summary`() = runTest {
        val metrics = TestMetrics()

        metrics.requests.observe(2.0)
        assertSamplesShouldMatchOnce(
            metrics.dump(), "requests", "summary", null,
            listOf(
                SampleMatcher("requests_count", Matcher.Eq(1.0)),
                SampleMatcher("requests_sum", Matcher.Eq(2.0))
            )
        )

        metrics.requests.observe(3.0)
        assertSamplesShouldMatchOnce(
            metrics.dump(), "requests", "summary", null,
            listOf(
                SampleMatcher("requests_count", Matcher.Eq(2.0)),
                SampleMatcher("requests_sum", Matcher.Eq(5.0))
            )
        )

        metrics.requests.measureTime {
            delay(10)
        }
        assertSamplesShouldMatchOnce(
            metrics.dump(), "requests", "summary", null,
            listOf(
                SampleMatcher("requests_count", Matcher.Eq(3.0)),
                SampleMatcher("requests_sum", Matcher.Gt(5.0))
            )
        )

        metrics.requestsLabeled.observe(8.2) { label = "42" }
        assertSamplesShouldMatchOnce(
            metrics.dump(), "requests_labeled", "summary", null,
            listOf(
                SampleMatcher("requests_labeled_count", Matcher.Eq(1.0), JustLabel().apply { label = "42" }),
                SampleMatcher("requests_labeled_sum", Matcher.Eq(8.2), JustLabel().apply { label = "42" })
            )
        )
    }

    @Test
    @JsName("observeHistogram")
    fun `observe histogram`() = runTest {
        val metrics = TestMetrics()
        assertNull(metrics.dump()["http_requests"])

        metrics.httpRequests.observe(1.0)

        val v1 = Matcher.Eq(1.0)
        val labels = ExactLabelsMatcher(LabelSet.EMPTY)
        val histLabels = { v: Int -> RegexLabelsMatcher(HistogramLabelSet("$v(.0)?")) }
        assertSamplesShouldMatchOnce(
            metrics.dump(), "http_requests", "histogram", null,
            listOf(
                SampleMatcher("http_requests_count", 1.0),
                SampleMatcher("http_requests_sum", 1.0),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(1)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(2)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(3)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(4)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(5)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(6)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(7)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(8)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(9)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(10)),
                SampleMatcher("http_requests_bucket", v1, labels, ExactLabelsMatcher(HistogramLabelSet("+Inf")))
            )
        )

        metrics.httpRequests.observe(3.5)
        metrics.httpRequests.observe(9.0)

        val v2 = Matcher.Eq(2.0)
        val v3 = Matcher.Eq(3.0)
        assertSamplesShouldMatchOnce(
            metrics.dump(), "http_requests", "histogram", null,
            listOf(
                SampleMatcher("http_requests_count", 3.0),
                SampleMatcher("http_requests_sum", 13.5),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(1)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(2)),
                SampleMatcher("http_requests_bucket", v1, labels, histLabels(3)),
                SampleMatcher("http_requests_bucket", v2, labels, histLabels(4)),
                SampleMatcher("http_requests_bucket", v2, labels, histLabels(5)),
                SampleMatcher("http_requests_bucket", v2, labels, histLabels(6)),
                SampleMatcher("http_requests_bucket", v2, labels, histLabels(7)),
                SampleMatcher("http_requests_bucket", v2, labels, histLabels(8)),
                SampleMatcher("http_requests_bucket", v3, labels, histLabels(9)),
                SampleMatcher("http_requests_bucket", v3, labels, histLabels(10)),
                SampleMatcher("http_requests_bucket", v3, labels, ExactLabelsMatcher(HistogramLabelSet("+Inf")))
            )
        )

        metrics.httpRequests.measureTime {
            delay(10)
        }
        assertSamplesShouldMatchAny(
            metrics.dump(), "http_requests", "histogram", null,
            listOf(
                SampleMatcher("http_requests_count", 4.0),
                SampleMatcher("http_requests_sum", Matcher.Gt(13.5)),
                SampleMatcher("http_requests_bucket", Matcher.Gte(1.0),
                    labels, RegexLabelsMatcher(HistogramLabelSet(".*")))
            )
        )
    }

    @Test
    @JsName("observeHistogramWithLabels")
    fun `observe histogram with labels`() = runTest {
        val metrics = TestMetrics()
        assertNull(metrics.dump()["http_requests_labeled"])

        metrics.httpRequestsLabeled.observe(1.0) {
            label = "hist-o-label"
        }

        val v1 = Matcher.Eq(1.0)
        val labels = ExactLabelsMatcher(JustLabel("hist-o-label"))
        val histLabels = { v: Int -> RegexLabelsMatcher(HistogramLabelSet("$v(.0)?")) }
        assertSamplesShouldMatchOnce(
            metrics.dump(), "http_requests_labeled", "histogram", null,
            listOf(
                SampleMatcher("http_requests_labeled_count", Matcher.Eq(1.0), labels),
                SampleMatcher("http_requests_labeled_sum", Matcher.Eq(1.0), labels),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(1)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(2)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(3)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(4)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(5)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(6)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(7)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(8)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(9)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(10)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, ExactLabelsMatcher(HistogramLabelSet("+Inf")))
            )
        )

        metrics.httpRequestsLabeled.observe(3.5) {
            label = "hist-o-label"
        }
        metrics.httpRequestsLabeled.observe(9.0) {
            label = "oh-label"
        }

        val v0 = Matcher.Eq(0.0)
        val v2 = Matcher.Eq(2.0)
        val ohLabels = ExactLabelsMatcher(JustLabel("oh-label"))
        assertSamplesShouldMatchOnce(
            metrics.dump(), "http_requests_labeled", "histogram", null,
            listOf(
                SampleMatcher("http_requests_labeled_count", Matcher.Eq(2.0), labels),
                SampleMatcher("http_requests_labeled_sum", Matcher.Eq(4.5), labels),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(1)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(2)),
                SampleMatcher("http_requests_labeled_bucket", v1, labels, histLabels(3)),
                SampleMatcher("http_requests_labeled_bucket", v2, labels, histLabels(4)),
                SampleMatcher("http_requests_labeled_bucket", v2, labels, histLabels(5)),
                SampleMatcher("http_requests_labeled_bucket", v2, labels, histLabels(6)),
                SampleMatcher("http_requests_labeled_bucket", v2, labels, histLabels(7)),
                SampleMatcher("http_requests_labeled_bucket", v2, labels, histLabels(8)),
                SampleMatcher("http_requests_labeled_bucket", v2, labels, histLabels(9)),
                SampleMatcher("http_requests_labeled_bucket", v2, labels, histLabels(10)),
                SampleMatcher("http_requests_labeled_bucket", v2, labels, ExactLabelsMatcher(HistogramLabelSet("+Inf"))),
                SampleMatcher("http_requests_labeled_count", Matcher.Eq(1.0), ohLabels),
                SampleMatcher("http_requests_labeled_sum", Matcher.Eq(9.0), ohLabels),
                SampleMatcher("http_requests_labeled_bucket", v0, ohLabels, histLabels(1)),
                SampleMatcher("http_requests_labeled_bucket", v0, ohLabels, histLabels(2)),
                SampleMatcher("http_requests_labeled_bucket", v0, ohLabels, histLabels(3)),
                SampleMatcher("http_requests_labeled_bucket", v0, ohLabels, histLabels(4)),
                SampleMatcher("http_requests_labeled_bucket", v0, ohLabels, histLabels(5)),
                SampleMatcher("http_requests_labeled_bucket", v0, ohLabels, histLabels(6)),
                SampleMatcher("http_requests_labeled_bucket", v0, ohLabels, histLabels(7)),
                SampleMatcher("http_requests_labeled_bucket", v0, ohLabels, histLabels(8)),
                SampleMatcher("http_requests_labeled_bucket", v1, ohLabels, histLabels(9)),
                SampleMatcher("http_requests_labeled_bucket", v1, ohLabels, histLabels(10)),
                SampleMatcher("http_requests_labeled_bucket", v1, ohLabels, ExactLabelsMatcher(HistogramLabelSet("+Inf"))),
            )
        )
    }

    @Test
    fun submetrics() = runTest {
        val metrics = NestedMetrics()

        metrics.nested.cpuUsagePercent.set(1.0)
        metrics.prefixed.cpuUsagePercent.set(100.0)
        assertSamplesShouldMatchAny(
            metrics.dump(), "cpu_usage_percent", "gauge", null,
            listOf(
                SampleMatcher("cpu_usage_percent", 1.0)
            )
        )
        assertSamplesShouldMatchAny(
            metrics.dump(), "prefixed_cpu_usage_percent", "gauge", null,
            listOf(
                SampleMatcher("prefixed_cpu_usage_percent", 100.0)
            )
        )
    }

    @Test
    @JsName("histogramWithoutBuckets")
    fun `histogram without buckets`() {
        assertFailsWith<IllegalArgumentException> {
            InvalidHistogram()
        }
    }

    @Test
    @JsName("clashingMetricNames")
    fun `clashing metric names`() {
        assertFailsWith(IllegalArgumentException::class) {
            ClashingMetrics()
        }.also { exc ->
            assertEquals("[gc_count] sample has already been added by [gcTime] metric", exc.message)
        }
    }

    @Test
    @JsName("clashingNestedMetricNames")
    fun `clashing nested metric names`() {
        assertFailsWith(IllegalArgumentException::class) {
            ClashingNestedMetrics()
        }.also { exc ->
            assertEquals("[nested_test] sample has already been added by [nested] sub-metrics", exc.message)
        }
    }
}
