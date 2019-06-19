package dev.evo.prometheus.jvm

import dev.evo.prometheus.Samples
import dev.evo.prometheus.runTest

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmMetricTests {
    @Test
    fun `jvm metrics`() = runTest {
        val metrics = DefaultJvmMetrics()
        assertEquals(metrics.dump(), emptyMap<String, Samples>())
        metrics.collect()
        println(metrics.dump())
        val samples = metrics.dump()
        assertEquals(samples, emptyMap<String, Samples>())

    }
}
