package dev.evo.prometheus

import kotlin.system.measureNanoTime

actual inline fun measureTimeMillis(block: () -> Unit): Double = measureNanoTime { block() }.toDouble() / 1_000_000.0
