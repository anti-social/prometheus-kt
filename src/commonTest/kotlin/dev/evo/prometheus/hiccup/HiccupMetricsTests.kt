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
                SampleMatcher("hiccups_count", 100.0),
                SampleMatcher("hiccups_sum", 990.0),
            ) + listOf(
                1.0 to 1.0,
                2.0 to 1.0,
                3.0 to 1.0,
                4.0 to 1.0,
                5.0 to 1.0,
                6.0 to 1.0,
                7.0 to 1.0,
                8.0 to 1.0,
                9.0 to 1.0,
                10.0 to 100.0,
                20.0 to 100.0,
                30.0 to 100.0,
                40.0 to 100.0,
                50.0 to 100.0,
                60.0 to 100.0,
                70.0 to 100.0,
                80.0 to 100.0,
                90.0 to 100.0,
                100.0 to 100.0,
                200.0 to 100.0,
                300.0 to 100.0,
                400.0 to 100.0,
                500.0 to 100.0,
                600.0 to 100.0,
                700.0 to 100.0,
                800.0 to 100.0,
                900.0 to 100.0,
                1000.0 to 100.0,
                2000.0 to 100.0,
                3000.0 to 100.0,
                4000.0 to 100.0,
                5000.0 to 100.0,
                6000.0 to 100.0,
                7000.0 to 100.0,
                8000.0 to 100.0,
                9000.0 to 100.0,
                10000.0 to 100.0,
                "+Inf" to 100.0,
            ).toHiccupsBucketSampleMatchers()
            metrics.dump().also { samples ->
                assertSamplesShouldMatchOnce(
                    samples, "hiccups", "histogram", null, firstDumpSampleMatchers
                )
            }

            timeSource += 15.milliseconds
            advanceTimeBy(10)
            runCurrent()
            metrics.dump().also { samples ->
                assertSamplesShouldMatchOnce(
                    samples, "hiccups", "histogram", null, firstDumpSampleMatchers
                )
            }

            timeSource += 2000.milliseconds
            advanceTimeBy(10)
            runCurrent()
            metrics.dump().also { samples ->
                assertSamplesShouldMatchOnce(
                    samples, "hiccups", "histogram", null,
                    listOf(
                        SampleMatcher("hiccups_count", 102.0),
                        SampleMatcher("hiccups_sum", 2985.0),
                    ) + listOf(
                        1.0 to 1.0,
                        2.0 to 1.0,
                        3.0 to 1.0,
                        4.0 to 1.0,
                        5.0 to 2.0,
                        6.0 to 2.0,
                        7.0 to 2.0,
                        8.0 to 2.0,
                        9.0 to 2.0,
                        10.0 to 101.0,
                        20.0 to 101.0,
                        30.0 to 101.0,
                        40.0 to 101.0,
                        50.0 to 101.0,
                        60.0 to 101.0,
                        70.0 to 101.0,
                        80.0 to 101.0,
                        90.0 to 101.0,
                        100.0 to 101.0,
                        200.0 to 101.0,
                        300.0 to 101.0,
                        400.0 to 101.0,
                        500.0 to 101.0,
                        600.0 to 101.0,
                        700.0 to 101.0,
                        800.0 to 101.0,
                        900.0 to 101.0,
                        1000.0 to 101.0,
                        2000.0 to 102.0,
                        3000.0 to 102.0,
                        4000.0 to 102.0,
                        5000.0 to 102.0,
                        6000.0 to 102.0,
                        7000.0 to 102.0,
                        8000.0 to 102.0,
                        9000.0 to 102.0,
                        10000.0 to 102.0,
                        "+Inf" to 102.0,
                    ).toHiccupsBucketSampleMatchers()
                )
            }
        } finally {
            hiccupsJob.cancel()
        }
    }
}
