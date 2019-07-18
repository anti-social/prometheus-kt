package dev.evo.prometheus

import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class LabelTests {
    class EmptyLabels : LabelSet()

    class NamedLabels : LabelSet() {
        var myLabel by label("my_label")
    }

    class SomeLabels : LabelSet() {
        var label1 by label()
        var label2 by label()
    }

    class SameLabels : LabelSet() {
        var label1 by label()
        var label2 by label()
    }

    class BulkOfLabels : LabelSet(2) {
        var label1 by label()
        var label2 by label()
        var label3 by label()
    }

    class TypedLabels : LabelSet() {
        var int by label<Int> { toString().reversed() }
        var double: Double? by label { toString().reversed() }
        var str by label<String>("string") { reversed() }
    }

    @Test
    @JsName("emptyLabels")
    fun `empty labels`() {
        val labels = EmptyLabels()
        assertEquals(LabelSet.EMPTY.hashCode(), labels.hashCode())
        assertEquals<LabelSet>(
            LabelSet.EMPTY,
            labels
        )
        assertEquals("", labels.toString())
    }

    @Test
    fun equality() {
        val someLabels = SomeLabels().apply {
            label1 = "1"
            label2 = "2"
        }
        val sameLabels = SameLabels().apply {
            label1 = "1"
            label2 = "2"
        }
        assertEquals(someLabels.hashCode(), sameLabels.hashCode())
        assertEquals<Any>(someLabels, sameLabels)
    }

    @Test
    fun escaping() {
        val labels = NamedLabels()
        assertEquals("", labels.toString())

        labels.myLabel = "\"\t\r\n\""
        assertEquals("""{my_label="\"\t\r\n\""}""", labels.toString())
    }

    @Test
    @JsName("numberOfLabelsIsMoreThanInitialCapacity")
    fun `number of labels is more than initial capacity`() {
        val labels = BulkOfLabels().apply {
            label1 = "99 bottles of beer"
            label2 = "98 bottles"
            label3 = "97"
        }
        assertEquals(
            "99 bottles of beer", labels.label1
        )
        assertEquals(
            "98 bottles", labels.label2
        )
        assertEquals(
            "97", labels.label3
        )
        assertEquals(
            """{label1="99 bottles of beer",label2="98 bottles",label3="97"}""",
            labels.toString()
        )

        labels.label2 = null
        assertNull(labels.label2)
        assertEquals(
            """{label1="99 bottles of beer",label3="97"}""",
            labels.toString()
        )

        assertEquals(
            BulkOfLabels().apply {
                label1 = "99 bottles of beer"
                label3 = "97"
            },
            labels
        )
        assertEquals(
            BulkOfLabels().apply {
                label1 = "99 bottles of beer"
                label3 = "97"
            }.hashCode(),
            labels.hashCode()
        )
        assertNotEquals(
            BulkOfLabels().apply {
                label1 = "99 bottles of beer"
                label3 = "9"
            },
            labels
        )
    }

    @Test
    @JsName("additionalLabels")
    fun `additional labels`() {
        val bulkOfLabels = BulkOfLabels().apply {
            label3 = "test"
        }
        val labels = NamedLabels().apply {
            myLabel = "1234"
        }

        assertEquals(
            """{label3="test",my_label="1234"}""",
            bulkOfLabels.toString(labels)
        )
        assertEquals(
            """{label3="test"}""",
            bulkOfLabels.toString(LabelSet.EMPTY)
        )
        assertEquals(
            """{my_label="1234",label3="test"}""",
            labels.toString(bulkOfLabels)
        )
    }

    @Test
    @JsName("typed labels")
    fun `typed labels`() {
        val labels = TypedLabels().apply {
            int = -99
            double = Double.POSITIVE_INFINITY
            str = "hello"
        }
        assertEquals(
            """{int="99-",double="ytinifnI",string="olleh"}""",
            labels.toString()
        )
    }
}