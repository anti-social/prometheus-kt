package dev.evo.prometheus.jvm

import dev.evo.prometheus.Matcher
import dev.evo.prometheus.RegexLabelsMatcher
import dev.evo.prometheus.SampleMatcher
import dev.evo.prometheus.assertSamplesShouldMatchAny
import dev.evo.prometheus.assertSamplesShouldMatchOnce
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@kotlinx.coroutines.ExperimentalCoroutinesApi
class JvmMetricTests {
    @Test
    fun `allocated bytes metric has never decreased`() = runTest {
        val threadMetricsProvider = object : JvmThreadMetricsProvider {
            override val threadCount = 0L
            override val daemonThreadCount = 0L
            override val peakThreadCount = 0L
            override val totalStartedThreadCount = 0L
            override val deadlockedThreadCount = 0L
            override val monitorDeadlockedThreadCount = 0L
            override val state = emptyMap<Thread.State, Int>()
            override var threadsAllocatedBytes = 100L
        }

        val threadMetrics = JvmThreadMetrics(threadMetricsProvider)

        threadMetrics.collect()
        assertEquals(threadMetrics.allocatedBytes.getMetricData(), 100L)

        threadMetricsProvider.threadsAllocatedBytes = 90L
        threadMetrics.collect()
        assertEquals(threadMetrics.allocatedBytes.getMetricData(), 100L)

        threadMetricsProvider.threadsAllocatedBytes = 95L
        threadMetrics.collect()
        assertEquals(threadMetrics.allocatedBytes.getMetricData(), 105L)
    }

    @Test
    fun `collect jvm metrics`() = runTest {
        val metrics = DefaultJvmMetrics()
        assertEquals(metrics.dump(), emptyMap())

        metrics.collect()
        val samples = metrics.dump()

        assertEquals(samples.size, 13)

        assertSamplesShouldMatchOnce(
            samples, "jvm_threads_current", "gauge", "Current thread count",
            listOf(
                SampleMatcher("jvm_threads_current", Matcher.Gt(0.0))
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "jvm_threads_daemon", "gauge", "Daemon thread count",
            listOf(
                SampleMatcher("jvm_threads_daemon", Matcher.Gt(0.0))
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "jvm_threads_peak", "gauge", "Peak thread count",
            listOf(
                SampleMatcher("jvm_threads_peak", Matcher.Gt(0.0))
            )
        )
        assertSamplesShouldMatchAny(
            samples, "jvm_threads_state", "gauge", "Current thread count by state",
            listOf(
                SampleMatcher("jvm_threads_state", Matcher.Gt(0.0), ThreadStateLabels(Thread.State.NEW)),
                SampleMatcher("jvm_threads_state", Matcher.Gt(0.0), ThreadStateLabels(Thread.State.NEW)),
                SampleMatcher("jvm_threads_state", Matcher.Gt(0.0), ThreadStateLabels(Thread.State.RUNNABLE)),
                SampleMatcher("jvm_threads_state", Matcher.Gt(0.0), ThreadStateLabels(Thread.State.BLOCKED)),
                SampleMatcher("jvm_threads_state", Matcher.Gt(0.0), ThreadStateLabels(Thread.State.WAITING)),
                SampleMatcher("jvm_threads_state", Matcher.Gt(0.0), ThreadStateLabels(Thread.State.TIMED_WAITING)),
                SampleMatcher("jvm_threads_state", Matcher.Gt(0.0), ThreadStateLabels(Thread.State.TERMINATED))
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "jvm_threads_deadlocked", "gauge",
            "Threads that are in deadlock to acquire object monitors or synchronizers",
            listOf(
                SampleMatcher("jvm_threads_deadlocked", 0.0)
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "jvm_threads_deadlocked_monitor", "gauge",
            "Threads that are in deadlock to acquire object monitors",
            listOf(
                SampleMatcher("jvm_threads_deadlocked_monitor", 0.0)
            )
        )
        assertSamplesShouldMatchOnce(
            samples, "jvm_threads_allocated_bytes", "gauge",
            "Total allocated bytes (may not take into account allocations of short-living threads)",
            listOf(
                SampleMatcher("jvm_threads_allocated_bytes", Matcher.Gt(0.0))
            )
        )

        val gcLabelsMatcher = RegexLabelsMatcher(GCLabels().apply { gcName = ".*" })
        assertSamplesShouldMatchAny(
            samples, "jvm_garbage_collection_count", "gauge",
            "Total number of the GCs",
            listOf(
                SampleMatcher("jvm_garbage_collection_count", Matcher.Gte(0.0), gcLabelsMatcher)

            )
        )
        assertSamplesShouldMatchAny(
            samples, "jvm_garbage_collection_time", "gauge",
            "Total time of the GCs",
            listOf(
                SampleMatcher("jvm_garbage_collection_time", Matcher.Gte(0.0), gcLabelsMatcher)
            )
        )

        val memoryLabelsMatcher = RegexLabelsMatcher(MemoryUsageLabels().apply { area = "heap|nonheap" })
        assertSamplesShouldMatchAny(
            samples, "jvm_memory_bytes_used", "gauge",
            "Amount of current used memory",
            listOf(
                SampleMatcher("jvm_memory_bytes_used", Matcher.Gte(0.0), memoryLabelsMatcher)

            )
        )
        assertSamplesShouldMatchAny(
            samples, "jvm_memory_bytes_committed", "gauge",
            "Amount of memory is committed for the JVM to use",
            listOf(
                SampleMatcher("jvm_memory_bytes_committed", Matcher.Gte(0.0), memoryLabelsMatcher)
            )
        )
        assertSamplesShouldMatchAny(
            samples, "jvm_memory_bytes_max", "gauge",
            "Maximum amount of memory that can be used for memory management",
            listOf(
                SampleMatcher("jvm_memory_bytes_max", Matcher.Gte(-1.0), memoryLabelsMatcher)
            )
        )
    }
}
