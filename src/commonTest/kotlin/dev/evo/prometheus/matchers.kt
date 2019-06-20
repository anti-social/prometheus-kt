package dev.evo.prometheus

import kotlin.test.assertNotNull

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
    class Gte<T: Comparable<T>>(private val gte: T) : Matcher<T> {
        override fun match(value: T) = when (value >= gte) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be greater then or equal $gte")
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

class LabelsMatcher(val labelMatchers: LabelSet) : Matcher<LabelSet> {
    override fun match(value: LabelSet): MatchResult {
        val failures = mutableListOf<String>()
        for ((labelName, labelValue) in value.labels) {
            val labelRegex = labelMatchers.labels[labelName]?.toRegex()
            if (labelRegex == null) {
                failures.add("Cannot find regex for a label: [$labelName]")
                continue
            }
            println("Matching $labelValue - $labelRegex")
            if (labelRegex.matchEntire(labelValue) == null) {
                failures.add("[$labelName=$labelValue] label did not match any regex")
            }
        }
        if (failures.isNotEmpty()) {
            return MatchResult.Fail(failures.joinToString("\n"))
        }
        return MatchResult.Ok
    }
}

class SampleMatcher(
    val name: String, val valueMatcher: Matcher<Double>, val labelsMatcher: LabelsMatcher? = null
) : Matcher<Sample> {
    override fun match(value: Sample): MatchResult {
        val failures = mutableListOf<String>()
        if (name != value.name) {
            failures.add("Sample name differs: expected [$name] but was [${value.name}]")
        }
        labelsMatcher?.match(value.labels)?.let {
            if (it is MatchResult.Fail) {
                failures.add(it.message)
            }
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
                "Samples name differs: expected [$name] but was [${value.name}]"
            )
        }
        if (value.type != type) {
            failures.add(
                "Samples type differs: expected [$type] but was [${value.type}]"
            )
        }
        if (value.help != help) {
            failures.add(
                "Samples help differs: expected [$help] but was [${value.help}]"
            )
        }
        if (failures.isNotEmpty()) {
            return MatchResult.Fail(failures.joinToString("\n"))
        }
        return MatchResult.Ok
    }
}

class OrderSamplesMatcher(
    name: String, type: String, help: String?,
    val sampleMatchers: List<SampleMatcher>
) : BaseSamplesMatcher(name, type, help) {
    override fun match(value: Samples): MatchResult {
        super.match(value).let {
            if (it is MatchResult.Fail) {
                return it
            }
        }

        for ((sampleMatcher, sample) in sampleMatchers.zip(value)) {
            val sampleMatchResult = sampleMatcher.match(sample)
            if (sampleMatchResult is MatchResult.Fail) {
                return sampleMatchResult
            }
        }
        return MatchResult.Ok
    }
}

class AllSamplesMatcher(
    name: String, type: String, help: String?,
    val sampleMatchers: Set<SampleMatcher>
) : BaseSamplesMatcher(name, type, help) {
    override fun match(value: Samples): MatchResult {
        super.match(value).let {
            if (it is MatchResult.Fail) {
                return it
            }
        }

        samplesLoop@for (sample in value) {
            val failures = mutableListOf<String>()
            matchersLoop@for (sampleMatcher in sampleMatchers) {
                when (val sampleMatchResult = sampleMatcher.match(sample)) {
                    is MatchResult.Ok -> continue@samplesLoop
                    is MatchResult.Fail -> {
                        failures.add(sampleMatchResult.message)
                        continue@matchersLoop
                    }
                }
            }
            return MatchResult.Fail("Cannot find any matched matchers for sample: $sample\n" +
                    failures.joinToString("\n"))
        }
        return MatchResult.Ok
    }
}

fun assertSamples(
    dumpedSamples: Map<String, Samples>, name: String, type: String, help: String?,
    sampleMatchers: List<SampleMatcher>
) {
    val samples = dumpedSamples[name]
    assertNotNull(samples, "Cannot find sampleMatchers with name: $name")
    OrderSamplesMatcher(name, type, help, sampleMatchers).assert(samples)
}

fun assertSamples(
    dumpedSamples: Map<String, Samples>, name: String, type: String, help: String?,
    sampleMatchers: Set<SampleMatcher>
) {
    val samples = dumpedSamples[name]
    assertNotNull(samples, "Cannot find sampleMatchers with name: $name")
    AllSamplesMatcher(name, type, help, sampleMatchers).assert(samples)
}
