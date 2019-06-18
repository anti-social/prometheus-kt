package dev.evo.prometheus

import kotlin.test.*

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

    private fun assertSamples(
        dumpedSamples: Map<String, Samples>, key: String, expectedSamples: Samples
    ) {
        val samples = dumpedSamples[key]
        assertNotNull(samples)
        assertEquals(expectedSamples.name, samples.name)
        assertEquals(expectedSamples.type, samples.type)
        assertEquals(expectedSamples.help, samples.help)
        assertEquals(expectedSamples.size, samples.size)
        for ((expectedSample, sample) in expectedSamples.sorted().zip(samples.sorted())) {
            assertEquals(expectedSample, sample)
        }
    }

    @Test
    fun `increment counter`() {
        val metrics = TestMetrics()
        metrics.gcCount.inc()
        assertSamples(metrics.dump(), "gc_count",
            Samples("gc_count", "counter", "GC counter").apply {
                add(Sample("gc_count", 1.0, LabelSet.EMPTY))
            })
    }

    @Test
    fun `increment counter with labels`() {
        val metrics = TestMetrics()
        metrics.processedMessages.inc()
        assertSamples(metrics.dump(), "processed_messages",
            Samples("processed_messages", "counter", null).apply {
                add(Sample("processed_messages", 1.0, LabelSet.EMPTY))
            })
        metrics.processedMessages.inc {
            topic = "test-topic-1"
        }
        assertSamples(metrics.dump(), "processed_messages",
            Samples("processed_messages", "counter", null).apply {
                add(Sample("processed_messages", 1.0, LabelSet.EMPTY))
                add(Sample("processed_messages", 1.0, KafkaLabels("test-topic-1")))
            })
        metrics.processedMessages.inc {
            topic = "test-topic-2"
        }
        assertSamples(metrics.dump(), "processed_messages",
            Samples("processed_messages", "counter", null).apply {
                add(Sample("processed_messages", 1.0, LabelSet.EMPTY))
                add(Sample("processed_messages", 1.0, KafkaLabels("test-topic-1")))
                add(Sample("processed_messages", 1.0, KafkaLabels("test-topic-2")))
            })
        metrics.processedMessages.inc {
            topic = "test-topic-2"
            routing = "test-routing-1"
        }
        assertSamples(metrics.dump(), "processed_messages",
            Samples("processed_messages", "counter", null).apply {
                add(Sample("processed_messages", 1.0, LabelSet.EMPTY))
                add(Sample("processed_messages", 1.0, KafkaLabels("test-topic-1")))
                add(Sample("processed_messages", 1.0, KafkaLabels("test-topic-2")))
                add(Sample("processed_messages", 1.0, KafkaLabels("test-topic-2", "test-routing-1")))
            })
    }

    @Test
    fun `set gauge`() {
        val metrics = TestMetrics()
        metrics.requestsInProcess.set(2.0)

        assertSamples(metrics.dump(), "requests_in_process",
            Samples("requests_in_process", "gauge", null).apply {
                add(Sample("requests_in_process", 2.0, LabelSet.EMPTY))
            })
    }

    @Test
    fun `increment then decrement gauge`() {
        val metrics = TestMetrics()
        metrics.requestsInProcess.incAndDec {
            assertSamples(metrics.dump(), "requests_in_process",
                Samples("requests_in_process", "gauge", null).apply {
                    add(Sample("requests_in_process", 1.0, LabelSet.EMPTY))
                })
        }

        assertSamples(metrics.dump(), "requests_in_process",
            Samples("requests_in_process", "gauge", null).apply {
                add(Sample("requests_in_process", 0.0, LabelSet.EMPTY))
            })
    }

    @Test
    fun `observe simple summary`() {
        val metrics = TestMetrics()
        metrics.summary.observe(2.0)
        assertSamples(metrics.dump(), "simple_summary",
            Samples("simple_summary", "summary", null).apply {
                add(Sample("simple_summary_count", 1.0, LabelSet.EMPTY))
                add(Sample("simple_summary_sum", 2.0, LabelSet.EMPTY))
            })
        metrics.summary.observe(3.0)
        assertSamples(metrics.dump(), "simple_summary",
            Samples("simple_summary", "summary", null).apply {
                add(Sample("simple_summary_count", 2.0, LabelSet.EMPTY))
                add(Sample("simple_summary_sum", 5.0, LabelSet.EMPTY))
            })
    }

    @Test
    fun `observe histogram`() {
        val metrics = TestMetrics()
        assertNull(metrics.dump()["http_requests"])
        metrics.httpRequests.observe(1.0)
        assertSamples(metrics.dump(), "http_requests",
            Samples("http_requests", "histogram", null).apply {
                add(Sample("http_requests_count", 1.0, LabelSet.EMPTY))
                add(Sample("http_requests_sum", 1.0, LabelSet.EMPTY))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("1.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("2.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("3.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("4.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("5.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("6.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("7.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("8.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("9.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("+Inf")))
            })
        metrics.httpRequests.observe(3.5)
        metrics.httpRequests.observe(9.0)
        assertSamples(metrics.dump(), "http_requests",
            Samples("http_requests", "histogram", null).apply {
                add(Sample("http_requests_count", 3.0, LabelSet.EMPTY))
                add(Sample("http_requests_sum", 13.5, LabelSet.EMPTY))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("1.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("2.0")))
                add(Sample("http_requests_bucket", 1.0, LabelSet.EMPTY, HistogramLabelSet("3.0")))
                add(Sample("http_requests_bucket", 2.0, LabelSet.EMPTY, HistogramLabelSet("4.0")))
                add(Sample("http_requests_bucket", 2.0, LabelSet.EMPTY, HistogramLabelSet("5.0")))
                add(Sample("http_requests_bucket", 2.0, LabelSet.EMPTY, HistogramLabelSet("6.0")))
                add(Sample("http_requests_bucket", 2.0, LabelSet.EMPTY, HistogramLabelSet("7.0")))
                add(Sample("http_requests_bucket", 2.0, LabelSet.EMPTY, HistogramLabelSet("8.0")))
                add(Sample("http_requests_bucket", 3.0, LabelSet.EMPTY, HistogramLabelSet("9.0")))
                add(Sample("http_requests_bucket", 3.0, LabelSet.EMPTY, HistogramLabelSet("+Inf")))
            })
    }

    @Test
    fun `clashing metric names`() {
        assertFailsWith(IllegalArgumentException::class) {
            ClashingMetrics()
        }.also { exc ->
            assertEquals("[gc_count] sample has already been added by [gcTime] metric", exc.message)
        }
    }
}
