package dev.evo.prometheus

import kotlinx.coroutines.runBlocking

actual fun runTest(block: suspend () -> Unit) = runBlocking {
    block()
}
