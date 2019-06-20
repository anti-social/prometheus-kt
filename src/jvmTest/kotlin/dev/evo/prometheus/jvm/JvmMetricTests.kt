package dev.evo.prometheus.jvm

import dev.evo.prometheus.LabelsMatcher
import dev.evo.prometheus.Matcher
import dev.evo.prometheus.SampleMatcher
import dev.evo.prometheus.Samples
import dev.evo.prometheus.assertSamples
import dev.evo.prometheus.runTest

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmMetricTests {
    @Test
    fun `collect jvm metrics`() = runTest {
        val metrics = DefaultJvmMetrics()
        assertEquals(metrics.dump(), emptyMap<String, Samples>())

        metrics.collect()
        val samples = metrics.dump()

        assertEquals(samples.size, 13)

        assertSamples(
            samples, "threads_current", "gauge", "Current thread count",
            listOf(
                SampleMatcher("threads_current", Matcher.Gt(0.0))
            )
        )
        assertSamples(
            samples, "threads_daemon", "gauge", "Daemon thread count",
            listOf(
                SampleMatcher("threads_daemon", Matcher.Gt(0.0))
            )
        )
        assertSamples(
            samples, "threads_peak", "gauge", "Peak thread count",
            listOf(
                SampleMatcher("threads_peak", Matcher.Gt(0.0))
            )
        )
        val threadStateLabelsMatcher = LabelsMatcher(
            ThreadStateLabels("NEW|RUNNABLE|BLOCKING|WAITING|TIMED_WAITING|TERMINATED")
        )
        assertSamples(
            samples, "threads_state", "gauge", "Current thread count by state",
            setOf(
                SampleMatcher("threads_state", Matcher.Gt(0.0), threadStateLabelsMatcher)
            )
        )
        assertSamples(
            samples, "threads_deadlocked", "gauge",
            "Threads that are in deadlock to aquire object monitors or synchronizers",
            listOf(
                SampleMatcher("threads_deadlocked", Matcher.Eq(0.0))
            )
        )
        assertSamples(
            samples, "threads_deadlocked_monitor", "gauge",
            "Threads that are in deadlock to aquire object monitors",
            listOf(
                SampleMatcher("threads_deadlocked_monitor", Matcher.Eq(0.0))
            )
        )
        assertSamples(
            samples, "threads_allocated_bytes", "gauge",
            "Total allocated bytes (may not take into account allocations of short-living threads)",
            listOf(
                SampleMatcher("threads_allocated_bytes", Matcher.Gt(0.0))
            )
        )

        val gcLabelsMatcher = LabelsMatcher(GCLabels().apply { gcName = ".*" })
        assertSamples(
            samples, "garbage_collection_count", "gauge",
            "Total number of the GCs",
            setOf(
                SampleMatcher("garbage_collection_count", Matcher.Gte(0.0), gcLabelsMatcher)

            )
        )
        assertSamples(
            samples, "garbage_collection_time", "gauge",
            "Total time of the GCs",
            setOf(
                SampleMatcher("garbage_collection_time", Matcher.Gte(0.0), gcLabelsMatcher)
            )
        )

        val memoryLabelsMatcher = LabelsMatcher(MemoryUsageLabels().apply { area = "heap|nonheap" })
        assertSamples(
            samples, "memory_bytes_used", "gauge",
            "Amount of current used memory",
            setOf(
                SampleMatcher("memory_bytes_used", Matcher.Gte(0.0), memoryLabelsMatcher)

            )
        )
        assertSamples(
            samples, "memory_bytes_committed", "gauge",
            "Amount of memory is committed for the JVM to use",
            setOf(
                SampleMatcher("memory_bytes_committed", Matcher.Gte(0.0), memoryLabelsMatcher)
            )
        )
        assertSamples(
            samples, "memory_bytes_max", "gauge",
            "Maximum amount of memory that can be used for memory management",
            setOf(
                SampleMatcher("memory_bytes_max", Matcher.Gte(-1.0), memoryLabelsMatcher)
            )
        )
    }
}
