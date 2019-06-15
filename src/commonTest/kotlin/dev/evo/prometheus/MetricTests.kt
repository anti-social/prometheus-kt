package dev.evo.prometheus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetricTests {

    object SimpleCounterMetrics : PrometheusMetrics() {
        val count by counter("count")
    }

    @Test
    fun `test counter`() {
        SimpleCounterMetrics.count.inc()
        val samples = SimpleCounterMetrics.dump()
        val sample = samples["count"]?.get(0)
        assertEquals(sample?.name, "count")
        assertEquals(sample?.value ?: -1.0, 1.0)
        assertEquals(sample?.baseLabels, LabelSet.EMPTY)
        assertNull(sample?.additionalLabels)
    }
}