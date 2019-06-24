package dev.evo.prometheus

import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val requestsInProcess by gauge("requests_in_process")
        val summary by simpleSummary("simple_summary")
        val httpRequests by histogram("http_requests", logScale(0, 0))
    }

    private class ClashingMetrics : PrometheusMetrics() {
        val gcCount by counter("gc_count", help = "GC counter")
        val gcTime by gauge("gc_count", help = "GC time")
    }

    private class ClashingNestedMetrics : PrometheusMetrics() {
        val nestedTest by counter("nested_test")
        val nested by submetrics(NestedMetrics())

        class NestedMetrics : PrometheusMetrics() {
            val test by counter("test")
        }
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
         metrics.requestsInProcess.set(2.0)

         assertSamplesShouldMatchOnce(
             metrics.dump(), "requests_in_process", "gauge", null,
             listOf(
                 SampleMatcher("requests_in_process", 2.0)
             )
         )
     }

     @Test
     @JsName("incrementThenDecrementGauge")
     fun `increment then decrement gauge`() = runTest {
         val metrics = TestMetrics()
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
     @JsName("observeSimpleSummary")
     fun `observe simple summary`() = runTest {
         val metrics = TestMetrics()
         metrics.summary.observe(2.0)
         assertSamplesShouldMatchOnce(
             metrics.dump(), "simple_summary", "summary", null,
             listOf(
                 SampleMatcher("simple_summary_count", Matcher.Eq(1.0)),
                 SampleMatcher("simple_summary_sum", Matcher.Eq(2.0))
             )
         )
         metrics.summary.observe(3.0)
         assertSamplesShouldMatchOnce(
             metrics.dump(), "simple_summary", "summary", null,
             listOf(
                 SampleMatcher("simple_summary_count", Matcher.Eq(2.0)),
                 SampleMatcher("simple_summary_sum", Matcher.Eq(5.0))
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
