package dev.evo.prometheus.proc

import kotlinx.coroutines.test.runTest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcfsMetricsTests {
    @Test
    fun test() = runTest {
        val procMetrics = ProcfsMetrics()
        procMetrics.collect()

        assertTrue(
            procMetrics.cpuSecondsTotal.get()!! >= 0.0,
            "CPU usage should be greater or equal 0"
        )
        assertTrue(
            procMetrics.memoryBytes.get(MemoryLabels("resident"))!! > 0.0,
            "Memory usage should be greater than 0"
        )
        assertTrue(
            procMetrics.openFiledesc.get()!! >= 1L,
            "Number of open files should be greater or equal 1"
        )
        assertTrue(
            procMetrics.startTimeSeconds.get()!! > 0L,
            "Process start time should be greater than 0"
        )
        assertTrue(
            procMetrics.numThreads.get()!! >= 1L,
            "There should be at least 1 thread"
        )
        assertTrue(
            procMetrics.threadStates.get(ThreadStateLabels(ThreadState.Running.name))!! >= 1L,
            "Number of running threads should be greater or equal 1"
        )
    }
}
