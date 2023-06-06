package dev.evo.prometheus.ktor

import dev.evo.prometheus.EmptyProcessMetrics
import dev.evo.prometheus.ProcessMetrics

actual val processMetrics: ProcessMetrics = EmptyProcessMetrics