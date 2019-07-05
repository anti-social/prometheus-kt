package dev.evo.prometheus

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class LabelSet {
    private val _labels = HashMap<String, String>()
    internal val labels: Map<String, String> = _labels

    object EMPTY : LabelSet()

    class LabelDelegateProvider<T>(
        private val name: String?,
        private val labelToString: T.() -> String = { toString() }
    ) {
        operator fun provideDelegate(thisRef: LabelSet, prop: KProperty<*>): ReadWriteProperty<LabelSet, T?> {
            var label: T? = null

            return object : ReadWriteProperty<LabelSet, T?> {
                override fun getValue(thisRef: LabelSet, property: KProperty<*>): T? {
                    return label
                }

                override fun setValue(thisRef: LabelSet, property: KProperty<*>, value: T?) {
                    label = value
                    if (value == null) {
                        thisRef._labels.remove(name ?: prop.name)
                    } else {
                        thisRef._labels[name ?: prop.name] = value.labelToString()
                    }
                }
            }
        }
    }
    fun label(name: String? = null) = LabelDelegateProvider<String>(name)
    fun <T> label(name: String? = null, labelToString: T.() -> String) = LabelDelegateProvider(name, labelToString)

    override fun equals(other: Any?): Boolean {
        if (other !is LabelSet) return false
        return _labels == other._labels
    }

    override fun hashCode(): Int {
        return _labels.hashCode()
    }

    override fun toString(): String {
        return toString(null)
    }

    fun toString(additionalLabels: LabelSet?): String {
        if (_labels.isEmpty() && additionalLabels?._labels.isNullOrEmpty()) {
            return ""
        }
        return sequenceOf(
            _labels.asSequence(),
            additionalLabels?._labels?.asSequence() ?: emptySequence()
        )
            .flatten()
            .joinToString(separator = ",", prefix = "{", postfix = "}") {
                "${it.key}=\"${it.value}\""
            }
    }
}
