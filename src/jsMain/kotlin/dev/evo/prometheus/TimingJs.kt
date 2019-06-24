package dev.evo.prometheus

typealias HrTimeResult = IntArray

fun HrTimeResult.toMillis(): Double {
    return this[0] * 1000.0 + this[1] / 1_000_000.0
}

external object process {
    fun hrtime(): HrTimeResult
    fun hrtime(prev: HrTimeResult): HrTimeResult
}

actual inline fun measureTimeMillis(block: () -> Unit): Double {
    val startAt = process.hrtime()
    block()
    val diffTime = process.hrtime(startAt)
    return diffTime.toMillis()
}
