package dev.evo.prometheus.nodejs

// import dev.evo.prometheus.writeSamples

import kotlin.test.Test
import kotlin.test.assertTrue

// import kotlinx.coroutines.GlobalScope
// import kotlinx.coroutines.promise
import kotlin.test.assertEquals

class NodeMetricTests {
    @Test
    fun test() {
        assertTrue(false)
    }

    // @Test
    // fun test() = GlobalScope.promise {
    //     val metrics = DefaultNodeMetrics()
    //     metrics.collect()
    //     val samples = metrics.dump()
    //     val sb = StringBuilder()
    //     writeSamples(samples, sb)
    //     val text = sb.toString()
    //     assertEquals("asdf", text)
    //
    //     println(memoryUsage().rss)
    //     println(memoryUsage().heapTotal)
    //     println(memoryUsage().heapUsed)
    //     getHeapSpaceStatistics().forEach {
    //         println(it.space_name)
    //         println(it.space_size)
    //         println(it.space_used_size)
    //     }
    //     assertTrue(false)
    // }
}
