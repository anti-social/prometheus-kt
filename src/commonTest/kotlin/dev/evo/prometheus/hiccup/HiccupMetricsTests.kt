package dev.evo.prometheus.hiccup

import dev.evo.prometheus.HistogramLabelSet
import dev.evo.prometheus.Matcher
import dev.evo.prometheus.RegexLabelsMatcher
import dev.evo.prometheus.SampleMatcher
import dev.evo.prometheus.assertSamplesShouldMatchAny

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

import kotlin.js.JsName
import kotlin.test.Test

class HiccupMetricsTests {
    @Test
    @JsName("hiccupsMetrics")
    fun `hiccup metrics`() = runTest {
        val metrics = HiccupMetrics()
        val hiccupsJob = metrics.startTracking(this)
        try {
            advanceTimeBy(1000)
            runCurrent()
            metrics.dump().also { samples ->
                assertSamplesShouldMatchAny(
                    samples, "hiccups", "histogram", null,
                    listOf(
                        SampleMatcher("hiccups_count", 100.0),
                        SampleMatcher("hiccups_sum", Matcher.Gte(0.0)),
                        SampleMatcher(
                            "hiccups_bucket", Matcher.Gte(0.0),
                            null, RegexLabelsMatcher(HistogramLabelSet(".*"))
                        )
                    )
                )
            }

            advanceTimeBy(10_000)
            runCurrent()
            metrics.dump().also { samples ->
                assertSamplesShouldMatchAny(
                    samples, "hiccups", "histogram", null,
                    listOf(
                        SampleMatcher("hiccups_count", 1100.0),
                        SampleMatcher("hiccups_sum", Matcher.Gte(0.0)),
                        SampleMatcher(
                            "hiccups_bucket", Matcher.Gte(0.0),
                            null, RegexLabelsMatcher(HistogramLabelSet(".*"))
                        )
                    )
                )
            }
        } finally {
            hiccupsJob.cancel()
        }
    }
}
