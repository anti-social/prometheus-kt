package dev.evo.prometheus.hiccup

import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.util.measureTimeMillis
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

expect val hiccupCoroutineDispatcher: CoroutineDispatcher

class HiccupMetrics : PrometheusMetrics() {
    val hiccups by histogram(
            "hiccups",
            listOf(5.0) + logScale(1, 3)
    )

    private val hiccupJob: AtomicRef<Job?> = atomic(null)

    fun startHiccup(
        coroutineScope: CoroutineScope,
        coroutineDispatcher: CoroutineDispatcher = hiccupCoroutineDispatcher,
        delayIntervalMs: Long = 10L
    ): Unit = with(coroutineScope) {
        val job = Job(coroutineContext[Job])
        if (!hiccupJob.compareAndSet(null, job)) {
            return
        }
        launch(coroutineDispatcher + job) {
            var maxMeasuredDelayMs = 0.0 // maximum measured delay in an interval
            var counter = 0L
            while (true) {
                val realDelayMs = measureTimeMillis {
                    delay(delayIntervalMs)
                }
                maxMeasuredDelayMs = maxOf(realDelayMs, maxMeasuredDelayMs)

                // Update hiccups metric once per second
                // TODO: Make it configurable
                if (counter % 1000L == 0L) {
                    hiccups.observe((maxMeasuredDelayMs - delayIntervalMs).coerceAtLeast(0.0))
                    maxMeasuredDelayMs = 0.0
                }
                counter++
            }
        }
    }

    fun stopHiccup() {
        hiccupJob.getAndSet(null)?.cancel()
    }
}
