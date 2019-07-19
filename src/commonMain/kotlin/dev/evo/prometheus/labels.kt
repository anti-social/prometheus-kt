package dev.evo.prometheus

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private const val DEFAULT_INITIAL_CAPACITY = 4

abstract class LabelSet(initialCapacity: Int = DEFAULT_INITIAL_CAPACITY) {
    private var labelsCount = 0
    private var labelNames = Array<String?>(initialCapacity) { null }
    private var labelValues = Array<String?>(initialCapacity) { null }

    object EMPTY : LabelSet()

    private fun addLabel(name: String): Int {
        val labelIx = labelsCount
        if (labelIx >= labelNames.size) {
            labelNames = Array((labelIx + 1) * 2) { ix ->
                if (ix < labelIx) {
                    labelNames[ix]
                } else {
                    null
                }
            }
            labelValues = Array((labelIx + 1) * 2) { ix ->
                if (ix < labelIx) {
                    labelValues[ix]
                } else {
                    null
                }
            }
        }
        labelNames[labelIx] = name
        labelsCount++
        return labelIx
    }

    private fun putValue(labelIx: Int, value: String?) {
        labelValues[labelIx] = value
    }

    class LabelDelegateProvider<T>(
        private val name: String?,
        private val labelToString: T.() -> String = { toString() }
    ) {
        operator fun provideDelegate(thisRef: LabelSet, prop: KProperty<*>): ReadWriteProperty<LabelSet, T?> {
            var label: T? = null
            val labelIx = thisRef.addLabel(name ?: prop.name)

            return object : ReadWriteProperty<LabelSet, T?> {
                override fun getValue(thisRef: LabelSet, property: KProperty<*>): T? {
                    return label
                }

                override fun setValue(thisRef: LabelSet, property: KProperty<*>, value: T?) {
                    label = value
                    thisRef.putValue(labelIx, value?.labelToString())
                }
            }
        }
    }
    fun label(name: String? = null) = LabelDelegateProvider<String>(name)
    fun <T> label(name: String? = null, labelToString: T.() -> String) = LabelDelegateProvider(name, labelToString)

    override fun equals(other: Any?): Boolean {
        if (other !is LabelSet) return false
        return labelNames.contentEquals(other.labelNames) &&
                labelValues.contentEquals(other.labelValues)
    }

    override fun hashCode(): Int {
        return labelNames.contentHashCode() * 31 + labelValues.contentHashCode()
    }

    override fun toString(): String {
        return toString(null)
    }

    internal fun labels(): Sequence<Pair<String, String>> = sequence {
        for (labelIx in 0 until labelsCount) {
            val name = labelNames[labelIx]
            val value = labelValues[labelIx]
            if (name != null && value != null) {
                yield(name to value)
            }
        }
    }

    fun toString(additionalLabels: LabelSet?): String {
        val sb = StringBuilder()
        writeTo(sb, additionalLabels)
        return sb.toString()
    }

    fun writeTo(writer: Appendable, additionalLabels: LabelSet? = null) {
        var foundAnyLabel = false
        val writeLabels: (Pair<String, String>) -> Unit = { (name, value) ->
            if (!foundAnyLabel) {
                writer.append('{')
                foundAnyLabel = true
            } else {
                writer.append(',')
            }
            writer.append(name)
            writer.append('=')
            writeQuoted(writer, value)
        }

        labels().forEach(writeLabels)
        if (additionalLabels != null) {
            additionalLabels.labels().forEach(writeLabels)
        }

        if (foundAnyLabel) {
            writer.append('}')
        }
    }
}

private fun writeQuoted(writer: Appendable, v: String) {
    writer.append('"')
    for (c in v) {
        when (c) {
            '"' -> writer.append("\\\"")
            '\\' -> writer.append("\\\\")
            '\r' -> writer.append("\\r")
            '\n' -> writer.append("\\n")
            '\t' -> writer.append("\\t")
            '\b' -> writer.append("\\b")
            else -> writer.append(c)
        }
    }
    writer.append('"')
}
