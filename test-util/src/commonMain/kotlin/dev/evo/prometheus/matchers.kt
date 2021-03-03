package dev.evo.prometheus

import kotlin.test.assertNotNull

sealed class MatchResult {
    object Ok : MatchResult()
    class Fail(val messages: List<String>) : MatchResult() {
        constructor(message: String) : this(listOf(message))
        override fun toString(): String = messages.joinToString("\n")
    }
}

interface Matcher<T> {
    fun match(value: T): MatchResult
    fun assert(value: T) {
        val result = match(value)
        if (result is MatchResult.Fail) {
            throw AssertionError(result.messages)
        }
    }

    class Eq<T: Comparable<T>>(private val eq: T) : Matcher<T> {
        override fun match(value: T) = when (value == eq) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be equal to $eq")
        }
        override fun toString() = "Matcher.Eq($eq)"
    }
    class Gt<T: Comparable<T>>(private val gt: T) : Matcher<T> {
        override fun match(value: T) = when (value > gt) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be greater then $gt")
        }
        override fun toString() = "Matcher.Gt($gt)"
    }
    class Gte<T: Comparable<T>>(private val gte: T) : Matcher<T> {
        override fun match(value: T) = when (value >= gte) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be greater then or equal $gte")
        }
        override fun toString() = "Matcher.Gte($gte)"
    }
    class Lt<T: Comparable<T>>(private val lt: T) : Matcher<T> {
        override fun match(value: T) = when (value > lt) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be less then $lt")
        }
        override fun toString() = "Matcher.Lt($lt)"
    }
    class Between<T: Comparable<T>>(private val start: T, private val end: T) : Matcher<T> {
        override fun match(value: T) = when (value >= start && value <= end) {
            true -> MatchResult.Ok
            false -> MatchResult.Fail("$value should be between [$start;$end]")
        }
        override fun toString() = "Matcher.Between(start=$start, end=$end)"
    }
}

abstract class LabelsMatcher(val labels: LabelSet) : Matcher<LabelSet>

class ExactLabelsMatcher(labels: LabelSet) : LabelsMatcher(labels) {
    override fun match(value: LabelSet): MatchResult {
        val failures = mutableListOf<String>()
        val expectedLabels = labels.labels().toMap()
        val matchLabels = value.labels().toMap()
        for ((labelName, labelValue) in matchLabels) {
            val expectedLabelValue = expectedLabels[labelName]
            if (expectedLabelValue == null) {
                failures.add("Extra label: [$labelName]")
            }
            if (labelValue != expectedLabelValue) {
                failures.add("Label value for [$labelName] differs: " +
                    "expected [$expectedLabelValue] but was [$labelValue]")
            }
        }
        if (matchLabels.size != expectedLabels.size) {
            val missingLabels = expectedLabels.keys.toSet() - matchLabels.keys
            failures.add("Missing labels: $missingLabels")
        }
        if (failures.isNotEmpty()) {
            return MatchResult.Fail(failures)
        }
        return MatchResult.Ok
    }

    override fun toString(): String {
        return "ExactLabelsMatcher(labels=$labels)"
    }
}

class RegexLabelsMatcher(labels: LabelSet) : LabelsMatcher(labels) {
    override fun match(value: LabelSet): MatchResult {
        if (labels !is LabelSet.EMPTY && value is LabelSet.EMPTY) {
            return MatchResult.Fail("Expected non-empty labels but was empty")
        }
        if (labels is LabelSet.EMPTY && value !is LabelSet.EMPTY) {
            return MatchResult.Fail("Expected empty labels but was: $value")
        }
        val failures = mutableListOf<String>()
        val expectedLabels = labels.labels().toMap()
        val matchLabels = value.labels().toMap()
        for ((labelName, labelValue) in matchLabels) {
            val labelRegex = expectedLabels[labelName]?.toRegex()
            if (labelRegex == null) {
                failures.add("Cannot find regex for a label: [$labelName]")
                continue
            }
            if (labelRegex.matchEntire(labelValue) == null) {
                failures.add("[$labelName=$labelValue] label did not match any regex")
            }
        }
        if (failures.isNotEmpty()) {
            return MatchResult.Fail(failures)
        }
        return MatchResult.Ok
    }

    override fun toString(): String {
        return "RegexLabelsMatcher(labels=$labels)"
    }
}

class SampleMatcher(
    val name: String, val valueMatcher: Matcher<Double>,
    labelsMatcher: LabelsMatcher?, internalLabelsMatcher: LabelsMatcher? = null
) : Matcher<Sample> {
    val labelsMatcher = labelsMatcher ?: ExactLabelsMatcher(LabelSet.EMPTY)
    val internalLabelsMatcher = internalLabelsMatcher ?: ExactLabelsMatcher(LabelSet.EMPTY)

    constructor(name: String, valueMatcher: Matcher<Double>,
                labels: LabelSet? = null, internalLabels: LabelSet? = null):
        this(name, valueMatcher,
            ExactLabelsMatcher(labels ?: LabelSet.EMPTY),
            ExactLabelsMatcher(internalLabels ?: LabelSet.EMPTY))

    constructor(name: String, value: Double, labels: LabelSet? = null, internalLabels: LabelSet? = null):
        this(name, Matcher.Eq(value), labels, internalLabels)

    override fun match(value: Sample): MatchResult {
        val failures = mutableListOf<String>()
        if (name != value.name) {
            failures.add("Sample name differs: expected [$name] but was [${value.name}]")
        }
        labelsMatcher.match(value.labels).let {
            if (it is MatchResult.Fail) {
                failures.add("Labels mismatch: $it")
            }
        }
        internalLabelsMatcher.match(value.additionalLabels ?: LabelSet.EMPTY).let {
            if (it is MatchResult.Fail) {
                failures.add("Labels mismatch: $it")
            }
        }
        valueMatcher.match(value.value).let {
            if (it is MatchResult.Fail) {
                failures.addAll(it.messages)
            }
        }
        if (failures.isNotEmpty()) {
            return MatchResult.Fail(failures)
        }
        return MatchResult.Ok
    }

    override fun toString(): String {
        return "SampleMatcher(" +
                "name=$name, valueMatcher=$valueMatcher, " +
                "labelsMatcher=$labelsMatcher, internalLabelsMatcher=$internalLabelsMatcher)"
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
            return MatchResult.Fail(failures)
        }
        return MatchResult.Ok
    }
}

class OnceSamplesMatcher(
    name: String, type: String, help: String?,
    val sampleMatchers: List<SampleMatcher>
) : BaseSamplesMatcher(name, type, help) {
    override fun match(value: Samples): MatchResult {
        super.match(value).let {
            if (it is MatchResult.Fail) {
                return it
            }
        }

        val restSampleMatchers = sampleMatchers.toMutableList()
        samplesLoop@for (sample in value) {
            val failures = mutableListOf<String>()
            val matchersIterator = restSampleMatchers.iterator()
            matchersLoop@for (sampleMatcher in matchersIterator) {
                when (val sampleMatchResult = sampleMatcher.match(sample)) {
                    is MatchResult.Ok -> {
                        matchersIterator.remove()
                        continue@samplesLoop
                    }
                    is MatchResult.Fail -> {
                        failures.addAll(sampleMatchResult.messages)
                        continue@matchersLoop
                    }
                }
            }
            return MatchResult.Fail(
                "Cannot find any matched matchers for sample: $sample\n" +
                    failures.joinToString("\n")
            )
        }
        if (restSampleMatchers.isNotEmpty()) {
            return MatchResult.Fail(
                "Some matchers did not match any sample: $restSampleMatchers"
            )
        }
        return MatchResult.Ok
    }
}

class AnySamplesMatcher(
    name: String, type: String, help: String?,
    val sampleMatchers: List<SampleMatcher>
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
                        failures.addAll(sampleMatchResult.messages)
                        continue@matchersLoop
                    }
                }
            }
            return MatchResult.Fail(
                "Cannot find any matched matchers for sample: $sample\n" +
                    failures.joinToString("\n")
            )
        }
        return MatchResult.Ok
    }
}

fun assertSamplesShouldMatchOnce(
    dumpedSamples: Map<String, Samples>, name: String, type: String, help: String?,
    sampleMatchers: List<SampleMatcher>
) {
    val samples = dumpedSamples[name]
    assertNotNull(samples, "Cannot find sampleMatchers with name: $name")
    OnceSamplesMatcher(name, type, help, sampleMatchers).assert(samples)
}

fun assertSamplesShouldMatchAny(
    dumpedSamples: Map<String, Samples>, name: String, type: String, help: String?,
    sampleMatchers: List<SampleMatcher>
) {
    val samples = dumpedSamples[name]
    assertNotNull(samples, "Cannot find sampleMatchers with name: $name")
    AnySamplesMatcher(name, type, help, sampleMatchers).assert(samples)
}
