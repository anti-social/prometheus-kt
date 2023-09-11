package dev.evo.prometheus.hiccup

import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.util.measureTimeMillis

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val DEFAULT_DELAY_INTERVAL = 10L
const val MEASURES_ARRAY_SIZE = 100
const val MEASURES_MAX_DURATION = 2_000

class HiccupMetrics : PrometheusMetrics() {
    val hiccups by histogram(
            "hiccups",
            listOf(5.0) + logScale(1, 3)
    )

    private val measures = DoubleArray(MEASURES_ARRAY_SIZE)

    fun startTracking(
        coroutineScope: CoroutineScope,
        delayIntervalMs: Long = DEFAULT_DELAY_INTERVAL,
    ): Job = with(coroutineScope) {
        launch {
            var ix = 0
            var measuresTimeMs = 0.0
            while (true) {
                val realDelayMs = measureTimeMillis {
                    delay(delayIntervalMs)
                }
                measuresTimeMs += realDelayMs

                measures[ix] = (realDelayMs - delayIntervalMs).coerceAtLeast(delayIntervalMs.toDouble())
                ix++

                if (ix == measures.size || measuresTimeMs >= MEASURES_MAX_DURATION) {
                    hiccups.observe(measures, 0, ix)
                    ix = 0
                    measuresTimeMs = 0.0
                }
            }
        }
    }
}
