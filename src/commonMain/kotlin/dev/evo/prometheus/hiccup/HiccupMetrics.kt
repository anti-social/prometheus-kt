package dev.evo.prometheus.hiccup

import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.util.measureTimeMillis

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

const val DEFAULT_DELAY_INTERVAL = 10L
const val MEASURES_DURATION = 1_000L

@kotlin.time.ExperimentalTime
class HiccupMetrics : PrometheusMetrics() {
    val hiccups by histogram(
            "hiccups",
            logScale(0, 3),
            help = "Maximum seen hiccup in 1 second interval (milliseconds)"
    )

    fun startTracking(
        coroutineScope: CoroutineScope,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        delayIntervalMs: Long = DEFAULT_DELAY_INTERVAL,
        timeSource: TimeSource = TimeSource.Monotonic,
    ): Job = with(coroutineScope) {
        launch(coroutineContext) {
            var measuresDurationMs = 0L
            var maxSeenDelayMs = 0L
            while (true) {
                val mark = timeSource.markNow()
                delay(delayIntervalMs)
                val realDelayMs = mark.elapsedNow().inWholeMilliseconds
                if (realDelayMs > maxSeenDelayMs) {
                    maxSeenDelayMs = realDelayMs
                }
                measuresDurationMs += realDelayMs

                if (measuresDurationMs >= MEASURES_DURATION) {
                    hiccups.observe((maxSeenDelayMs - delayIntervalMs).coerceAtLeast(0L).toDouble())
                    maxSeenDelayMs = 0L
                    measuresDurationMs = 0L
                }
            }
        }
    }
}
