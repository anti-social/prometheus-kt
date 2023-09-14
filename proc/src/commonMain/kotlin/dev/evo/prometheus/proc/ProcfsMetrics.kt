package dev.evo.prometheus.proc

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PlatformMetrics

import okio.FileNotFoundException
import okio.FileSystem
import okio.Path.Companion.toPath

private const val USER_HZ = 100.0

expect val FS: FileSystem

enum class ThreadState {
    Running,
    Sleeping,
    Waiting,
    Zombie,
    Other;

    companion object {
        fun fromStateChar(c: Char): ThreadState {
            return when (c) {
                'R' -> Running
                'S' -> Sleeping
                'D' -> Waiting
                'Z' -> Zombie
                else -> Other
            }
        }
    }
}

class ThreadStateLabels(state: String? = null) : LabelSet() {
    var state by label("state")

    init {
        if (state != null) {
            this.state = state
        }
    }
}

class MemoryLabels(memType: String? = null) : LabelSet() {
    var memType by label("memtype")

    init {
        if (memType != null) {
            this.memType = memType
        }
    }
}

class ProcfsMetrics : PlatformMetrics() {
    val cpuSecondsTotal by counter(
        "process_cpu_seconds_total",
        "CPU usage in seconds"
    )
    val memoryBytes by gaugeLong(
        "process_memory_bytes",
        "Used memory"
    ) {
        MemoryLabels()
    }
    val openFiledesc by gaugeLong(
        "process_open_filedesc",
        "Number of open file descriptors"
    )
    val majorPageFaultsTotal by counterLong(
        "process_major_page_faults_total",
        "Number of major page faults"
    )
    val minorPageFaultsTotal by counterLong(
        "process_minor_page_faults_total",
        "Number of minor page faults"
    )
    val startTimeSeconds by gaugeLong(
        "process_start_time_seconds",
        "Epoch time at which process started"
    )
    val numThreads by gaugeLong(
        "process_num_threads",
        "Number of threads"
    )
    val threadStates by gaugeLong(
        "process_thread_states",
        "Number of threads by a state"
    ) {
        ThreadStateLabels()
    }

    private val statPath = "/proc/self/stat".toPath()

    private val tasksPath = "/proc/self/task".toPath()
    private val fdPath = "/proc/self/fd".toPath()

    override suspend fun collect() {
        FS.read(statPath) {
            val statsParser = StatsParser(readUtf8())
            val pid = statsParser.readField()
            val comm = statsParser.readCommField()
            val state = statsParser.readField()
            val ppid = statsParser.readField()
            val pgrp = statsParser.readField()
            val session = statsParser.readField()
            val ttyNr = statsParser.readField()
            val tpgid = statsParser.readField()
            val flags = statsParser.readField()
            val minflt = statsParser.readField().toLong()
            minorPageFaultsTotal.add(minflt)
            val cminflt = statsParser.readField()
            val majflt = statsParser.readField().toLong()
            majorPageFaultsTotal.add(majflt)
            val cmajflt = statsParser.readField()
            val utime = statsParser.readField().toLong()
            val stime = statsParser.readField().toLong()
            cpuSecondsTotal.add((utime + stime) / USER_HZ)
            val cutime = statsParser.readField().toLong()
            val cstime = statsParser.readField().toLong()
            val priority = statsParser.readField()
            val nice = statsParser.readField()
            val numThreads = statsParser.readField().toLong()
            this@ProcfsMetrics.numThreads.add(numThreads)
            val itRealValue = statsParser.readField()
            val startTime = statsParser.readField().toLong()
            startTimeSeconds.set(startTime)
            val vsize = statsParser.readField().toLong()
            memoryBytes.set(vsize) {
                memType = "virtual"
            }
            val rss = statsParser.readField().toLong()
            memoryBytes.set(rss) {
                memType = "resident"
            }
        }

        for (taskPath in FS.list(tasksPath)) {
            try {
                FS.read(taskPath / "stat") {
                    val statsParser = StatsParser(readUtf8())
                    val pid = statsParser.readField()
                    val comm = statsParser.readCommField()
                    val state = statsParser.readField()
                    threadStates.inc {
                        this.state = ThreadState.fromStateChar(state.first()).name
                    }
                }
            } catch (e: FileNotFoundException) {}
        }

        val numOpenFiles = FS.list(fdPath).size.toLong()
        openFiledesc.set(numOpenFiles)
    }
}

private class StatsParser(private var line: String) {
    fun readField(): String {
        return readUntil(' ').also { skip(1) }
    }

    fun readCommField(): String {
        return skip(1).readUntil(')').also { skip(2) }
    }

    fun readWhile(needle: Char): String {
        var ix = 0
        for (c in line) {
            if (c != needle) {
                return line.substring(0..ix).also {
                    line = line.substring(ix + 1 until line.length)
                }
            }
            ix++
        }
        return line.also {
            line = ""
        }
    }

    fun readUntil(needle: Char): String {
        var ix = 0
        for (c in line) {
            if (c == needle) {
                return line.substring(0 until ix).also {
                    line = line.substring(ix until line.length)
                }
            }
            ix++
        }
        return line.also {
            line = ""
        }
    }

    fun skip(n: Int): StatsParser {
        line = line.drop(n)
        return this
    }

    fun skipWhile(needle: Char): StatsParser {
        line = line.dropWhile { it == needle }
        return this
    }
}
