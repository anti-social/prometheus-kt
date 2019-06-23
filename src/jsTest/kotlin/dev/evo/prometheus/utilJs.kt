package dev.evo.prometheus

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

actual fun runTest(block: suspend () -> Unit): dynamic = GlobalScope.promise {
    block()
}