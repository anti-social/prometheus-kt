package dev.evo.prometheus.hiccup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext

// FIXME: Dispatchers.Default consumes too much CPU with tight delay
// See https://github.com/Kotlin/kotlinx.coroutines/issues/840
@UseExperimental(kotlinx.coroutines.ObsoleteCoroutinesApi::class)
actual val hiccupCoroutineDispatcher: CoroutineDispatcher = newSingleThreadContext("hiccup-thread")
