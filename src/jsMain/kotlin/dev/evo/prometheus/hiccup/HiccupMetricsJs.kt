package dev.evo.prometheus.hiccup

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

typealias HrTimeResult = IntArray

fun HrTimeResult.toMillis(): Long {
    return this[0] * 1000L + this[1] / 1_000_000
}

external object process {
    fun hrtime(): HrTimeResult
    fun hrtime(prev: HrTimeResult): HrTimeResult
}

actual val hiccupCoroutineContext: CoroutineContext = Dispatchers.Default

actual inline fun measureTime(block: () -> Unit): Long {
    val startAt = process.hrtime()
    block()
    val diffTime = process.hrtime(startAt)
    return diffTime.toMillis()
}
