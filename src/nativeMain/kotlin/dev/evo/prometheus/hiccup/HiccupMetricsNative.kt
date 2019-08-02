package dev.evo.prometheus.hiccup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val hiccupCoroutineDispatcher: CoroutineDispatcher = Dispatchers.Default
