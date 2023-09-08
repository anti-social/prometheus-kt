package dev.evo.prometheus.hiccup

import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.util.measureTimeMillis

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val DEFAULT_DELAY_INTERVAL = 10L
const val MEASURES_ARRAY_SIZE = 100

@OptIn(kotlin.ExperimentalStdlibApi::class)
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
        launch(coroutineContext) {
            var ix = 0
            while (true) {
                val realDelayMs = measureTimeMillis {
                    delay(delayIntervalMs)
                }
                measures[ix] = (realDelayMs - delayIntervalMs).coerceAtLeast(delayIntervalMs.toDouble())
                ix++

                if (ix == measures.size) {
                    hiccups.observe(measures)
                    ix = 0
                }
            }
        }
    }
}
