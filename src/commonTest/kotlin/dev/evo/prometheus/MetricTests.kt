package dev.evo.prometheus

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
        val requestsInProcess by gauge("requests_in_process")
        val summary by simpleSummary("simple_summary")
        val httpRequests by histogram("http_requests", scale(1.0))
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
     fun `observe histogram`() = runTest {
         val metrics = TestMetrics()
         assertNull(metrics.dump()["http_requests"])

         metrics.httpRequests.observe(1.0)
         assertSamplesShouldMatchOnce(
             metrics.dump(), "http_requests", "histogram", null,
             listOf(
                 SampleMatcher("http_requests_count", 1.0),
                 SampleMatcher("http_requests_sum", 1.0),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("1.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("2.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("3.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("4.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("5.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("6.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("7.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("8.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("9.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("+Inf"))
             )
         )

         metrics.httpRequests.observe(3.5)
         metrics.httpRequests.observe(9.0)
         assertSamplesShouldMatchOnce(
             metrics.dump(), "http_requests", "histogram", null,
             listOf(
                 SampleMatcher("http_requests_count", 3.0),
                 SampleMatcher("http_requests_sum", 13.5),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("1.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("2.0")),
                 SampleMatcher("http_requests_bucket", 1.0, null, HistogramLabelSet("3.0")),
                 SampleMatcher("http_requests_bucket", 2.0, null, HistogramLabelSet("4.0")),
                 SampleMatcher("http_requests_bucket", 2.0, null, HistogramLabelSet("5.0")),
                 SampleMatcher("http_requests_bucket", 2.0, null, HistogramLabelSet("6.0")),
                 SampleMatcher("http_requests_bucket", 2.0, null, HistogramLabelSet("7.0")),
                 SampleMatcher("http_requests_bucket", 2.0, null, HistogramLabelSet("8.0")),
                 SampleMatcher("http_requests_bucket", 3.0, null, HistogramLabelSet("9.0")),
                 SampleMatcher("http_requests_bucket", 3.0, null, HistogramLabelSet("+Inf"))
             )
         )
     }

    @Test
    fun `clashing metric names`() {
        assertFailsWith(IllegalArgumentException::class) {
            ClashingMetrics()
        }.also { exc ->
            assertEquals("[gc_count] sample has already been added by [gcTime] metric", exc.message)
        }
    }

    @Test
    fun `clashing nested metric names`() {
        assertFailsWith(IllegalArgumentException::class) {
            ClashingNestedMetrics()
        }.also { exc ->
            assertEquals("[nested_test] sample has already been added by [nested] sub-metrics", exc.message)
        }
    }
}
