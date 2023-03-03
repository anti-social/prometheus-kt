@file:JvmName("JvmKt")

package dev.evo.prometheus.ktor

import dev.evo.prometheus.ProcessMetrics
import dev.evo.prometheus.jvm.DefaultJvmMetrics

actual val processMetrics: ProcessMetrics = DefaultJvmMetrics()