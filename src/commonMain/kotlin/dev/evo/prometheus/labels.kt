package dev.evo.prometheus

import kotlin.reflect.KProperty

abstract class LabelSet {
    private val _labels = HashMap<String, String>()
    internal val labels: Map<String, String> = _labels

    object EMPTY : LabelSet()

    class LabelDelegate(private val name: String?) {
        operator fun getValue(thisRef: LabelSet, prop: KProperty<*>): String? {
            return thisRef._labels[name ?: prop.name]
        }

        operator fun setValue(thisRef: LabelSet, prop: KProperty<*>, value: String?) {
            if (value == null) {
                thisRef._labels.remove(name ?: prop.name)
            } else {
                thisRef._labels[name ?: prop.name] = value
            }
        }
    }
    fun label(name: String? = null) = LabelDelegate(name)

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
