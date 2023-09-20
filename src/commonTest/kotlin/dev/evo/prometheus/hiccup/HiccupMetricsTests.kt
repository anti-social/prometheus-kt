package dev.evo.prometheus.hiccup

import dev.evo.prometheus.HistogramLabelSet
import dev.evo.prometheus.Matcher
import dev.evo.prometheus.RegexLabelsMatcher
import dev.evo.prometheus.SampleMatcher
import dev.evo.prometheus.assertSamplesShouldMatchOnce
import dev.evo.prometheus.ExactLabelsMatcher

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

import kotlin.js.JsName
import kotlin.test.Test
import kotlin.time.TestTimeSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@kotlin.time.ExperimentalTime
class HiccupMetricsTests {
    private fun List<Pair<Any, Double>>.toHiccupsBucketSampleMatchers(): List<SampleMatcher> {
        return map { (le, v) ->
            val histogramLabelsMatcher = if (le is String) {
                ExactLabelsMatcher(HistogramLabelSet(le))
            } else {
                RegexLabelsMatcher(HistogramLabelSet("$le(.0)?"))
            }
            SampleMatcher(
                "hiccups_bucket",
                Matcher.Eq(v),
                labelsMatcher = null,
                internalLabelsMatcher = histogramLabelsMatcher,
            )
        }
    }

    @Test
    @JsName("hiccupsMetrics")
    fun `hiccup metrics`() = runTest {
        val timeSource = TestTimeSource()
        val metrics = HiccupMetrics()
        val hiccupsJob = metrics.startTracking(
            this,
            timeSource = timeSource,
        )
        try {
            timeSource += 10.milliseconds
            advanceTimeBy(10)
            runCurrent()
            repeat(99) {
                timeSource += 20.milliseconds
                advanceTimeBy(10)
                runCurrent()
            }

            val firstDumpSampleMatchers = listOf(
                SampleMatcher("hiccups_count", 1.0),
                SampleMatcher("hiccups_sum", 10.0),
            ) + listOf(
                1.0 to 0.0,
                2.0 to 0.0,
                3.0 to 0.0,
                4.0 to 0.0,
                5.0 to 0.0,
                6.0 to 0.0,
                7.0 to 0.0,
                8.0 to 0.0,
                9.0 to 0.0,
                10.0 to 1.0,
                20.0 to 1.0,
                30.0 to 1.0,
                40.0 to 1.0,
                50.0 to 1.0,
                60.0 to 1.0,
                70.0 to 1.0,
                80.0 to 1.0,
                90.0 to 1.0,
                100.0 to 1.0,
                200.0 to 1.0,
                300.0 to 1.0,
                400.0 to 1.0,
                500.0 to 1.0,
                600.0 to 1.0,
                700.0 to 1.0,
                800.0 to 1.0,
                900.0 to 1.0,
                1000.0 to 1.0,
                2000.0 to 1.0,
                3000.0 to 1.0,
                4000.0 to 1.0,
                5000.0 to 1.0,
                6000.0 to 1.0,
                7000.0 to 1.0,
                8000.0 to 1.0,
                9000.0 to 1.0,
                10000.0 to 1.0,
                "+Inf" to 1.0,
            ).toHiccupsBucketSampleMatchers()
            metrics.dump().also { samples ->
                assertSamplesShouldMatchOnce(
                    samples, "hiccups", "histogram",
                    help = "Maximum seen hiccup in 1 second interval (milliseconds)",
                    sampleMatchers = firstDumpSampleMatchers
                )
            }

            timeSource += 15.milliseconds
            advanceTimeBy(10)
            runCurrent()
            metrics.dump().also { samples ->
                assertSamplesShouldMatchOnce(
                    samples, "hiccups", "histogram",
                    help = "Maximum seen hiccup in 1 second interval (milliseconds)",
                    sampleMatchers = firstDumpSampleMatchers
                )
            }

            timeSource += 2000.milliseconds
            advanceTimeBy(10)
            runCurrent()
            metrics.dump().also { samples ->
                assertSamplesShouldMatchOnce(
                    samples, "hiccups", "histogram",
                    help = "Maximum seen hiccup in 1 second interval (milliseconds)",
                    listOf(
                        SampleMatcher("hiccups_count", 2.0),
                        SampleMatcher("hiccups_sum", 2000.0),
                    ) + listOf(
                        1.0 to 0.0,
                        2.0 to 0.0,
                        3.0 to 0.0,
                        4.0 to 0.0,
                        5.0 to 0.0,
                        6.0 to 0.0,
                        7.0 to 0.0,
                        8.0 to 0.0,
                        9.0 to 0.0,
                        10.0 to 1.0,
                        20.0 to 1.0,
                        30.0 to 1.0,
                        40.0 to 1.0,
                        50.0 to 1.0,
                        60.0 to 1.0,
                        70.0 to 1.0,
                        80.0 to 1.0,
                        90.0 to 1.0,
                        100.0 to 1.0,
                        200.0 to 1.0,
                        300.0 to 1.0,
                        400.0 to 1.0,
                        500.0 to 1.0,
                        600.0 to 1.0,
                        700.0 to 1.0,
                        800.0 to 1.0,
                        900.0 to 1.0,
                        1000.0 to 1.0,
                        2000.0 to 2.0,
                        3000.0 to 2.0,
                        4000.0 to 2.0,
                        5000.0 to 2.0,
                        6000.0 to 2.0,
                        7000.0 to 2.0,
                        8000.0 to 2.0,
                        9000.0 to 2.0,
                        10000.0 to 2.0,
                        "+Inf" to 2.0,
                    ).toHiccupsBucketSampleMatchers()
                )
            }
        } finally {
            hiccupsJob.cancel()
        }
    }
}
