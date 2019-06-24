package dev.evo.prometheus.hiccup

import kotlinx.coroutines.newSingleThreadContext
import kotlin.coroutines.CoroutineContext

// FIXME: Dispatchers.Default consumes too much CPU with tight delay
// See https://github.com/Kotlin/kotlinx.coroutines/issues/840
@UseExperimental(kotlinx.coroutines.ObsoleteCoroutinesApi::class)
actual val hiccupCoroutineContext: CoroutineContext = newSingleThreadContext("hiccup-thread")
