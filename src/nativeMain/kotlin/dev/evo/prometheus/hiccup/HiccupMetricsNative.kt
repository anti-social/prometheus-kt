package dev.evo.prometheus.hiccup

import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis

import kotlinx.coroutines.Dispatchers

actual val hiccupCoroutineContext: CoroutineContext = Dispatchers.Default

actual inline fun measureTime(block: () -> Unit): Long = measureTimeMillis(block)

