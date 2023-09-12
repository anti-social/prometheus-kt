package dev.evo.prometheus.hiccup

import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.util.measureTimeMillis

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

const val DEFAULT_DELAY_INTERVAL = 10L
const val MEASURES_ARRAY_SIZE = 100
const val MEASURES_MAX_DURATION = 2_000

@kotlin.time.ExperimentalTime
class HiccupMetrics : PrometheusMetrics() {
    val hiccups by histogram(
            "hiccups",
            logScale(0, 3)
    )

    private val measures = DoubleArray(MEASURES_ARRAY_SIZE)

    fun startTracking(
        coroutineScope: CoroutineScope,
        delayIntervalMs: Long = DEFAULT_DELAY_INTERVAL,
        timeSource: TimeSource = TimeSource.Monotonic,
    ): Job = with(coroutineScope) {
        launch {
            var ix = 0
            var measuresTimeMs = 0L
            while (true) {
                val mark = timeSource.markNow()
                delay(delayIntervalMs)
                val realDelayMs = mark.elapsedNow().inWholeMilliseconds
                measuresTimeMs += realDelayMs
                measures[ix] = (realDelayMs - delayIntervalMs).coerceAtLeast(0L).toDouble()
                ix++

                if (ix == measures.size || measuresTimeMs >= MEASURES_MAX_DURATION) {
                    hiccups.observe(measures, 0, ix)
                    ix = 0
                    measuresTimeMs = 0L
                }
            }
        }
    }
}
