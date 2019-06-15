package dev.evo.prometheus

import kotlinx.coroutines.runBlocking

// expect fun runTest(block: suspend () -> Unit)
actual fun runTest(block: suspend () -> Unit) = runBlocking {
    block()
}
