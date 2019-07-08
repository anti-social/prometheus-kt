package dev.evo.prometheus

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class LabelSet(initialCapacity: Int = 4) {
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
        return labelNames.hashCode() * 31 + labelValues.hashCode()
    }

    override fun toString(): String {
        return toString(null)
    }

    internal val labels: Map<String, String>
        get () = HashMap<String, String>(labelsCount).apply {
            labelNames.zip(labelValues)
                .forEach { (name, value) ->
                    if (name != null && value != null) {
                        put(name, value)
                    }
                }
        }

    fun toString(additionalLabels: LabelSet?): String {
        return sequenceOf(
            labelNames.asSequence().zip(labelValues.asSequence()),
            if (additionalLabels != null) {
                additionalLabels.labelNames.asSequence().zip(additionalLabels.labelValues.asSequence())
            } else {
                emptySequence()
            }
        )
            .flatten()
            .filter { (name, value) -> name != null && value != null }
            .joinToString(",", prefix = "{", postfix = "}") { (name, value) ->
                "$name=\"$value\""
            }
    }
}
