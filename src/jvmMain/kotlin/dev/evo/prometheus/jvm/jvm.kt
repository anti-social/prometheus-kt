package dev.evo.prometheus.jvm

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics

import java.lang.management.ManagementFactory

class DefaultJvmMetrics : PrometheusMetrics() {
    val memory by submetrics(JvmMemoryMetrics())
    val gc by submetrics("garbage_collection", JvmGcMetrics())
    val threads by submetrics(JvmThreadMetrics())
}

class MemoryUsageLabels : LabelSet() {
    var area by label()
}

class GCLabels : LabelSet() {
    var gcName by label("gc_name")
}

class JvmMemoryMetrics : PrometheusMetrics() {
    val memoryUsed by gaugeLong(
            "bytes_used"
    ) {
        MemoryUsageLabels()
    }
    val memoryCommitted by gaugeLong(
            "bytes_committed"
    ) {
        MemoryUsageLabels()
    }
    val memoryMax by gaugeLong(
            "bytes_max"
    ) {
        MemoryUsageLabels()
    }

    private val memory = ManagementFactory.getMemoryMXBean()

    override fun collect() {
        memoryUsed.set(memory.heapMemoryUsage.used) {
            area = "heap"
        }
        memoryCommitted.set(memory.heapMemoryUsage.committed) {
            area = "heap"
        }
        memoryMax.set(memory.heapMemoryUsage.max) {
            area = "heap"
        }
        memoryUsed.set(memory.nonHeapMemoryUsage.used) {
            area = "nonheap"
        }
        memoryCommitted.set(memory.nonHeapMemoryUsage.committed) {
            area = "nonheap"
        }
        memoryMax.set(memory.nonHeapMemoryUsage.max) {
            area = "nonheap"
        }
    }
}

class JvmGcMetrics : PrometheusMetrics() {
    val count by gaugeLong(
            "count",
            help = "Total number of the GCs"
    ) {
        GCLabels()
    }
    val time by gaugeLong(
            "time",
            help = "Total time of the GCs"
    ) {
        GCLabels()
    }

    private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()

    override fun collect() {
        for (gc in gcBeans) {
            count.set(gc.collectionCount) {
                gcName = gc.name
            }
            time.set(gc.collectionTime) {
                gcName = gc.name
            }
        }
    }
}

class ThreadStateLabels : LabelSet() {
    var state by label()
}

class JvmThreadMetrics : PrometheusMetrics() {
    val current by gaugeLong(
            "current",
            help = "Current thread count"
    )
    val daemon by gaugeLong(
            "daemon",
            help = "Daemon thread count"
    )
    val peak by gaugeLong(
            "peak",
            help = "Peak thread count"
    )
    val startedTotal by gaugeLong(
            "started_total",
            help = "Started thread count"
    )
    val deadlocked by gaugeLong(
            "deadlocked",
            help = "Threads that are in deadlock to aquire object monitors or synchronizers"
    )
    val deadlockedMonitor by gaugeLong(
            "deadlocked_monitor",
            help = "Threads that are in deadlock to aquire object monitors"
    )
    val state by gaugeLong(
            "state",
            help = "Current thread count by state"
    ) {
        ThreadStateLabels()
    }
    val allocatedBytes by gauge(
            "allocated_bytes",
            help = "Total allocated bytes " +
                    "(may not take into account allocations of short-living threads)"
    )

    private val threadBean = ManagementFactory.getThreadMXBean()

    override fun collect() {
        current.set(threadBean.threadCount.toLong())
        daemon.set(threadBean.daemonThreadCount.toLong())
        peak.set(threadBean.peakThreadCount.toLong())
        startedTotal.set(threadBean.totalStartedThreadCount)
        deadlocked.set(threadBean.findDeadlockedThreads()?.size?.toLong() ?: 0L)
        deadlockedMonitor.set(threadBean.findMonitorDeadlockedThreads()?.size?.toLong() ?: 0L)
        val allThreadIds = threadBean.allThreadIds
        val threadStateCounts = HashMap<Thread.State, Int>(6)
        threadBean.getThreadInfo(allThreadIds).forEach {
            threadStateCounts.compute(it.threadState) { _, oldCount ->
                (oldCount ?: 0) + 1
            }
        }
        threadStateCounts.forEach { (threadState, count) ->
            state.set(count.toLong()) {
                state = threadState.toString()
            }
        }

        val sunThreadBean = (threadBean as? com.sun.management.ThreadMXBean)
        if (sunThreadBean != null) {
            sunThreadBean.getThreadAllocatedBytes(allThreadIds)
                    .asSequence()
                    .filter { it > 0 }
                    .map { it.toDouble() }
                    .sum()
                    .also { allocatedBytes.set(it) }
        }
    }
}
