package dev.evo.prometheus.ktor

import dev.evo.prometheus.PlatformMetrics
import dev.evo.prometheus.jvm.DefaultJvmMetrics

actual val platformMetrics: PlatformMetrics = DefaultJvmMetrics()
