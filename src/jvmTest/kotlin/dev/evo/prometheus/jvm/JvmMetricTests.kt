package dev.evo.prometheus.jvm

import dev.evo.prometheus.RegexLabelsMatcher
import dev.evo.prometheus.Matcher
import dev.evo.prometheus.SampleMatcher
import dev.evo.prometheus.Samples
import dev.evo.prometheus.assertSamplesShouldMatchAny
import dev.evo.prometheus.assertSamplesShouldMatchOnce
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

        assertSamplesShouldMatchOnce(
            samples, "threads_current", "gauge", "Current thread count",
            listOf(
                SampleMatcher("threads_current", Matcher.Gt(0.0))
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "threads_daemon", "gauge", "Daemon thread count",
            listOf(
                SampleMatcher("threads_daemon", Matcher.Gt(0.0))
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "threads_peak", "gauge", "Peak thread count",
            listOf(
                SampleMatcher("threads_peak", Matcher.Gt(0.0))
            )
        )
        val threadStateLabelsMatcher = RegexLabelsMatcher(
            ThreadStateLabels("NEW|RUNNABLE|BLOCKING|WAITING|TIMED_WAITING|TERMINATED")
        )
        assertSamplesShouldMatchAny(
            samples, "threads_state", "gauge", "Current thread count by state",
            listOf(
                SampleMatcher("threads_state", Matcher.Gt(0.0), threadStateLabelsMatcher)
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "threads_deadlocked", "gauge",
            "Threads that are in deadlock to aquire object monitors or synchronizers",
            listOf(
                SampleMatcher("threads_deadlocked", 0.0)
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "threads_deadlocked_monitor", "gauge",
            "Threads that are in deadlock to aquire object monitors",
            listOf(
                SampleMatcher("threads_deadlocked_monitor", 0.0)
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "threads_allocated_bytes", "gauge",
            "Total allocated bytes (may not take into account allocations of short-living threads)",
            listOf(
                SampleMatcher("threads_allocated_bytes", Matcher.Gt(0.0))
            )
        )

        val gcLabelsMatcher = RegexLabelsMatcher(GCLabels().apply { gcName = ".*" })
        assertSamplesShouldMatchAny(
            samples, "garbage_collection_count", "gauge",
            "Total number of the GCs",
            listOf(
                SampleMatcher("garbage_collection_count", Matcher.Gte(0.0), gcLabelsMatcher)

            )
        )
        assertSamplesShouldMatchAny(
            samples, "garbage_collection_time", "gauge",
            "Total time of the GCs",
            listOf(
                SampleMatcher("garbage_collection_time", Matcher.Gte(0.0), gcLabelsMatcher)
            )
        )

        val memoryLabelsMatcher = RegexLabelsMatcher(MemoryUsageLabels().apply { area = "heap|nonheap" })
        assertSamplesShouldMatchAny(
            samples, "memory_bytes_used", "gauge",
            "Amount of current used memory",
            listOf(
                SampleMatcher("memory_bytes_used", Matcher.Gte(0.0), memoryLabelsMatcher)

            )
        )
        assertSamplesShouldMatchAny(
            samples, "memory_bytes_committed", "gauge",
            "Amount of memory is committed for the JVM to use",
            listOf(
                SampleMatcher("memory_bytes_committed", Matcher.Gte(0.0), memoryLabelsMatcher)
            )
        )
        assertSamplesShouldMatchAny(
            samples, "memory_bytes_max", "gauge",
            "Maximum amount of memory that can be used for memory management",
            listOf(
                SampleMatcher("memory_bytes_max", Matcher.Gte(-1.0), memoryLabelsMatcher)
            )
        )
    }
}
