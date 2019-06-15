package dev.evo.prometheus.hiccup

import dev.evo.prometheus.PrometheusMetrics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlin.system.measureTimeMillis

class HiccupMetrics : PrometheusMetrics() {
    val hiccups by histogram(
            "hiccups",
            listOf(5.0) + scale(10.0) + scale(100.0) + listOf(1000.0)
    )

    fun startTracking(coroutineScope: CoroutineScope, delayIntervalMs: Long = 10L) = with(coroutineScope) {
        // FIXME: Dispatchers.Default consumes too much CPU with tight delay
        // See https://github.com/Kotlin/kotlinx.coroutines/issues/840
        val hiccupCoroutineContext = newSingleThreadContext("hiccups-thread")
        launch(hiccupCoroutineContext) {
            var maxMeasuredDelayMs = 0L // maximum measured delay in an interval
            var counter = 0L
            while (true) {
                val realDelayMs = measureTimeMillis {
                    delay(delayIntervalMs)
                }
                maxMeasuredDelayMs = maxOf(realDelayMs, maxMeasuredDelayMs)

                // Update hiccups metric once per second
                // TODO: Make it configurable
                if (counter % 1000L == 0L) {
                    hiccups.observe((maxMeasuredDelayMs - delayIntervalMs).coerceAtLeast(0L).toDouble())
                    maxMeasuredDelayMs = 0L
                }
                counter++
            }
        }
    }
}
