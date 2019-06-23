package dev.evo.prometheus

import kotlinx.coroutines.CoroutineScope

expect fun runTest(block: suspend CoroutineScope.() -> Unit)
