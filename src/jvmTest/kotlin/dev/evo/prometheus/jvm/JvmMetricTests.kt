package dev.evo.prometheus.jvm

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.MetricKey
import dev.evo.prometheus.Sample
import dev.evo.prometheus.Samples
import dev.evo.prometheus.runTest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

sealed class MatchResult {
    object Ok : MatchResult()
    class Fail(val message: String) : MatchResult()
}

interface Matcher<T> {
    fun match(value: T): MatchResult
    fun assert(value: T) {
        val result = match(value)
        if (result is MatchResult.Fail) {
            throw AssertionError(result.message)
        }
    }

    class Eq<T: Comparable<T>>(private val eq: T) : Matcher<T> {
        override fun match(value: T) = when (value == eq) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be equal to $eq")
        }
    }
    class Gt<T: Comparable<T>>(private val gt: T) : Matcher<T> {
        override fun match(value: T) = when (value > gt) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be greater then $gt")
        }
    }
    class Lt<T: Comparable<T>>(private val lt: T) : Matcher<T> {
        override fun match(value: T) = when (value > lt) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be less then $lt")
        }
    }
    class Between<T: Comparable<T>>(private val start: T, private val end: T) : Matcher<T> {
        override fun match(value: T) = when (value >= start && value <= end) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be between [$start;$end]")
        }
    }
}

class SampleMatcher(val name: String, val valueMatcher: Matcher<Double>, val labels: LabelSet) : Matcher<Sample> {
    override fun match(value: Sample): MatchResult {
        val failures = mutableListOf<String>()
        if (name != value.name) {
            failures.add("Sample name differs: expected $name but was ${value.name}")
        }
        if (labels != value.labels) {
            failures.add("Sample labels differ: expected $labels but was ${value.labels}")
        }
        valueMatcher.match(value.value).let {
            if (it is MatchResult.Fail) {
                failures.add(it.message)
            }
        }

        if (failures.isNotEmpty()) {
            return MatchResult.Fail(failures.joinToString("\n"))
        }
        return MatchResult.Ok
    }
}

abstract class BaseSamplesMatcher(
    val name: String, val type: String, val help: String?
) : Matcher<Samples> {
    override fun match(value: Samples): MatchResult {
        val failures = mutableListOf<String>()
        if (value.name != name) {
            failures.add(
                "Samples name differs: expected $name but was ${value.name}"
            )
        }
        if (value.type != type) {
            failures.add(
                "Samples type differs: expected $type but was ${value.type}"
            )
        }
        if (value.help != help) {
            failures.add(
                "Samples help differs: expected $help but was ${value.help}"
            )
        }
        if (failures.isNotEmpty()) {
            return MatchResult.Fail(failures.joinToString("\n"))
        }
    }
}

class OrderSamplesMatcher(
    name: String, type: String, help: String?,
    val samples: List<SampleMatcher>
) : BaseSamplesMatcher(name, type, help) {
    override fun match(value: Samples): MatchResult {
        super.match(value)
        for ((sampleMatcher, sample) in samples.zip(value)) {
            val sampleMatchResult = sampleMatcher.match(sample)
            if (sampleMatchResult is MatchResult.Fail) {
                return sampleMatchResult
            }
        }
        return MatchResult.Ok
    }
}

class AnySamplesMatcher(
    name: String, val type: String, val help: String?,
    val samples: Set<SampleMatcher>
) : Matcher<Samples> {
    override fun match(value: Samples): MatchResult {
        val failures = mutableListOf<String>()
        if (value.name != name) {
            failures.add(
                "Samples name differs: expected $name but was ${value.name}"
            )
        }
        if (value.type != type) {
            failures.add(
                "Samples type differs: expected $type but was ${value.type}"
            )
        }
        if (value.help != help) {
            failures.add(
                "Samples help differs: expected $help but was ${value.help}"
            )
        }
        if (value.size != samples.size) {
            failures.add(
                "Samples count differs: expected ${samples.size} but was ${value.size}"
            )
        }
        if (failures.isNotEmpty()) {
            return MatchResult.Fail(failures.joinToString("\n"))
        }
        val sampleMatchers = samples.map { MetricKey(it.name, it.labels) to it }.toMap()
        for (sample in value) {
            val sampleMatcher = sampleMatchers[MetricKey(sample.name, sample.labels)]
                ?: return MatchResult.Fail("Cannot find matcher for sample: $sample")
            sampleMatcher.match(sample).let {
                if (it is MatchResult.Fail) {
                    return it
                }
            }
        }
        return MatchResult.Ok
    }
}

class JvmMetricTests {
    private fun assertSamples(
        dumpedSamples: Map<String, Samples>, samplesMatcher: Matcher<Samples>
    ) {
        val samples = dumpedSamples[samplesMatcher]
        assertNotNull(samples)
        samplesMatcher.assert(samples)
    }

    @Test
    fun `jvm metrics`() = runTest {
        val metrics = DefaultJvmMetrics()
        assertEquals(metrics.dump(), emptyMap<String, Samples>())

        metrics.collect()
        val samples = metrics.dump()

        assertEquals(samples.size, 13)

        assertSamples(
            samples["threads_current"],
            OrderSamplesMatcher(
                "threads_current", "gauge",
                "Current thread count",
                listOf(
                    SampleMatcher("threads_current", Matcher.Gt(0.0), LabelSet.EMPTY)
                )
            )
        )

        assertSamples(
            samples["threads_current"],
            OrderSamplesMatcher(
                "threads_current", "gauge",
                "Current thread count",
                listOf(
                    SampleMatcher("threads_current", Matcher.Gt(0.0), LabelSet.EMPTY)
                )
            )
        )
//        assertSamples(samples, "threads_daemon", "gauge",
//            "Daemon thread count",
//            listOf(
//                ExpectedSample("threads_daemon", Matcher.Gt(0.0), LabelSet.EMPTY)
//            )
//        )
//        assertSamples(samples, "threads_peak", "gauge",
//            "Peak thread count",
//            listOf(
//                ExpectedSample("threads_peak", Matcher.Gt(0.0), LabelSet.EMPTY)
//            )
//        )
////        assertSamples(samples, "threads_state", "gauge",
////            "Current thread count by state",
////            listOf(
////                ExpectedSample("threads_state", Matcher.Gt(0.0), ThreadStateLabels("TIMED_WAITING")),
////                ExpectedSample("threads_state", Matcher.Gt(0.0), ThreadStateLabels("RUNNABLE")),
////                ExpectedSample("threads_state", Matcher.Gt(0.0), ThreadStateLabels("WAITING"))
////            )
////        )
//        assertSamples(samples, "threads_deadlocked", "gauge",
//            "Threads that are in deadlock to aquire object monitors or synchronizers",
//            listOf(
//                ExpectedSample("threads_deadlocked", Matcher.Eq(0.0), LabelSet.EMPTY)
//            )
//        )
//        assertSamples(samples, "threads_deadlocked_monitor", "gauge",
//            "Threads that are in deadlock to aquire object monitors",
//            listOf(
//                ExpectedSample("threads_deadlocked_monitor", Matcher.Eq(0.0), LabelSet.EMPTY)
//            )
//        )
//        assertSamples(samples, "garbage_collection_count", "gauge",
//            "Total number of the GCs",
//            listOf(
//                ExpectedSample("garbage_collection_count", Matcher.Eq(0.0), LabelSet.EMPTY),
//                ExpectedSample("garbage_collection_count", Matcher.Eq(0.0), LabelSet.EMPTY)
//            )
//        )
    }
}
