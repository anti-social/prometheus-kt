package dev.evo.prometheus.hiccup

import dev.evo.prometheus.HistogramLabelSet
import dev.evo.prometheus.Matcher
import dev.evo.prometheus.RegexLabelsMatcher
import dev.evo.prometheus.SampleMatcher
import dev.evo.prometheus.assertSamplesShouldMatchAny

import kotlinx.coroutines.test.runBlockingTest

import kotlin.test.Test

@kotlinx.coroutines.ExperimentalCoroutinesApi
class HiccupMetricsTests {
    @Test
    fun `hiccup metrics`() = runBlockingTest {
        val metrics = HiccupMetrics()
        val hiccupsJob = metrics.startTracking(this, coroutineContext)
        try {
            advanceTimeBy(20)

            metrics.dump().also { samples ->
                assertSamplesShouldMatchAny(
                    samples, "hiccups", "histogram", null,
                    listOf(
                        SampleMatcher("hiccups_count", 1.0),
                        SampleMatcher("hiccups_sum", Matcher.Gte(0.0)),
                        SampleMatcher("hiccups_bucket", Matcher.Gte(0.0),
                            null, RegexLabelsMatcher(HistogramLabelSet(".*")))
                    )
                )
            }

            advanceTimeBy(10_000)

            metrics.dump().also { samples ->
                assertSamplesShouldMatchAny(
                    samples, "hiccups", "histogram", null,
                    listOf(
                        SampleMatcher("hiccups_count", 2.0),
                        SampleMatcher("hiccups_sum", Matcher.Gte(0.0)),
                        SampleMatcher("hiccups_bucket", Matcher.Gte(0.0),
                            null, RegexLabelsMatcher(HistogramLabelSet(".*")))
                    )
                )
            }
        } finally {
            hiccupsJob.cancel()
        }
    }
}