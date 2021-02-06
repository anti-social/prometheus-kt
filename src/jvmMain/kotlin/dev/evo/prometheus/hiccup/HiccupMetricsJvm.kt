package dev.evo.prometheus.hiccup

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

// TODO: Check high CPU consumption
// See https://github.com/Kotlin/kotlinx.coroutines/issues/840
actual val hiccupCoroutineContext: CoroutineContext = Dispatchers.Default
