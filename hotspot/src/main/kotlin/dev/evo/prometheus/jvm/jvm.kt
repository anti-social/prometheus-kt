package dev.evo.prometheus.jvm

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics

import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong

class DefaultJvmMetrics : PrometheusMetrics() {
    val memory by submetrics(JvmMemoryMetrics())
    val gc by submetrics(JvmGcMetrics())
    val threads by submetrics(JvmThreadMetrics())
}

class MemoryUsageLabels : LabelSet() {
    var area by label()
}

class GCLabels : LabelSet() {
    var gcName by label("gc_name")
}

class JvmMemoryMetrics : PrometheusMetrics() {
    private val prefix = "jvm_memory"

    val memoryUsed by gaugeLong(
            "${prefix}_bytes_used",
            help = "Amount of current used memory"
    ) {
        MemoryUsageLabels()
    }
    val memoryCommitted by gaugeLong(
            "${prefix}_bytes_committed",
            help = "Amount of memory is committed for the JVM to use"
    ) {
        MemoryUsageLabels()
    }
    val memoryMax by gaugeLong(
            "${prefix}_bytes_max",
            help = "Maximum amount of memory that can be used for memory management"
    ) {
        MemoryUsageLabels()
    }

    private val memory = ManagementFactory.getMemoryMXBean()

    override suspend fun collect() {
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
    private val prefix = "jvm_garbage_collection"

    val count by gaugeLong(
            "${prefix}_count",
            help = "Total number of the GCs"
    ) {
        GCLabels()
    }
    val time by gaugeLong(
            "${prefix}_time",
            help = "Total time of the GCs"
    ) {
        GCLabels()
    }

    private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()

    override suspend fun collect() {
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

class ThreadStateLabels(state: Thread.State? = null) : LabelSet() {
    var state: Thread.State? by label { toString() }

    init {
        if (state != null) {
            this.state = state
        }
    }
}

internal interface JvmThreadMetricsProvider {
    val threadCount: Long
    val daemonThreadCount: Long
    val peakThreadCount: Long
    val totalStartedThreadCount: Long
    val deadlockedThreadCount: Long
    val monitorDeadlockedThreadCount: Long
    val state: Map<Thread.State, Int>
    val threadsAllocatedBytes: Long?
}

internal object DefaultJvmThreadMetricsProvider : JvmThreadMetricsProvider {
    private val threadBean = ManagementFactory.getThreadMXBean()

    override val threadCount get() = threadBean.threadCount.toLong()
    override val daemonThreadCount get() = threadBean.daemonThreadCount.toLong()
    override val peakThreadCount get() = threadBean.peakThreadCount.toLong()
    override val totalStartedThreadCount get() = threadBean.totalStartedThreadCount
    override val deadlockedThreadCount get() =
        threadBean.findDeadlockedThreads()?.size?.toLong() ?: 0L
    override val monitorDeadlockedThreadCount get() =
        threadBean.findMonitorDeadlockedThreads()?.size?.toLong() ?: 0L
    override val state get() = buildMap {
        threadBean.getThreadInfo(threadBean.allThreadIds).filterNotNull().forEach {
            compute(it.threadState) { _, oldCount ->
                (oldCount ?: 0) + 1
            }
        }
    }
    override val threadsAllocatedBytes get() = (threadBean as? com.sun.management.ThreadMXBean)
        ?.getThreadAllocatedBytes(threadBean.allThreadIds)
        ?.asSequence()
        ?.filter { it > 0 }
        ?.sum()
}

class JvmThreadMetrics internal constructor(
    private val metricsProvider: JvmThreadMetricsProvider
) : PrometheusMetrics() {
    private val prefix = "jvm_threads"

    val current by gaugeLong(
            "${prefix}_current",
            help = "Current thread count"
    )
    val daemon by gaugeLong(
            "${prefix}_daemon",
            help = "Daemon thread count"
    )
    val peak by gaugeLong(
            "${prefix}_peak",
            help = "Peak thread count"
    )
    val startedTotal by gaugeLong(
            "${prefix}_started_total",
            help = "Started thread count"
    )
    val deadlocked by gaugeLong(
            "${prefix}_deadlocked",
            help = "Threads that are in deadlock to acquire object monitors or synchronizers"
    )
    val deadlockedMonitor by gaugeLong(
            "${prefix}_deadlocked_monitor",
            help = "Threads that are in deadlock to acquire object monitors"
    )
    val state by gaugeLong(
            "${prefix}_state",
            help = "Current thread count by state"
    ) {
        ThreadStateLabels()
    }
    val allocatedBytes by gaugeLong(
            "${prefix}_allocated_bytes",
            help = "Total allocated bytes " +
                    "(may not take into account allocations of short-living threads)"
    )

    private val baseAllocSize = AtomicLong()

    constructor() : this(DefaultJvmThreadMetricsProvider)

    override suspend fun collect() {
        current.set(metricsProvider.threadCount)
        daemon.set(metricsProvider.daemonThreadCount)
        peak.set(metricsProvider.peakThreadCount)
        startedTotal.set(metricsProvider.totalStartedThreadCount)
        deadlocked.set(metricsProvider.deadlockedThreadCount)
        deadlockedMonitor.set(metricsProvider.monitorDeadlockedThreadCount)
        metricsProvider.state.forEach { (threadState, count) ->
            state.set(count.toLong()) {
                state = threadState
            }
        }
        metricsProvider.threadsAllocatedBytes?.let { threadsAllocatedBytes ->
            val adjustedThreadsAllocatedBytes = threadsAllocatedBytes + baseAllocSize.get()
            val previousAllocatedBytes = allocatedBytes.get() ?: 0L
            if (adjustedThreadsAllocatedBytes < previousAllocatedBytes) {
                baseAllocSize.addAndGet(previousAllocatedBytes - adjustedThreadsAllocatedBytes)
            } else {
                allocatedBytes.set(adjustedThreadsAllocatedBytes)
            }
        }
    }
}
