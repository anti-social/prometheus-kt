package dev.evo.prometheus.push

import kotlin.test.Test
import kotlin.test.assertEquals

import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray

class Base64CodecTests {
    @Test
    fun testEncodeWikiExamples() {
        val codec = Base64Codec()
        assertEquals(
            "YW55IGNhcm5hbCBwbGVhc3VyZS4=",
            codec.encode("any carnal pleasure.".toByteArray(Charsets.UTF_8))
        )
        assertEquals(
            "YW55IGNhcm5hbCBwbGVhc3VyZQ==",
            codec.encode("any carnal pleasure".toByteArray(Charsets.UTF_8))
        )
        assertEquals(
            "YW55IGNhcm5hbCBwbGVhc3Vy",
            codec.encode("any carnal pleasur".toByteArray(Charsets.UTF_8))
        )
        assertEquals(
            "YW55IGNhcm5hbCBwbGVhc3U=",
            codec.encode("any carnal pleasu".toByteArray(Charsets.UTF_8))
        )
    }

    @Test
    fun testEncodeWikiText() {
        val codec = Base64Codec()
        val text = "" +
                "Man is distinguished, not only by his reason, but by this singular passion from other animals, " +
                "which is a lust of the mind, that by a perseverance of delight in the continued and indefatigable " +
                "generation of knowledge, exceeds the short vehemence of any carnal pleasure."
        assertEquals(
            "TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ1dCBieSB0aGlz" +
                    "IHNpbmd1bGFyIHBhc3Npb24gZnJvbSBvdGhlciBhbmltYWxzLCB3aGljaCBpcyBhIGx1c3Qgb2Yg" +
                    "dGhlIG1pbmQsIHRoYXQgYnkgYSBwZXJzZXZlcmFuY2Ugb2YgZGVsaWdodCBpbiB0aGUgY29udGlu" +
                    "dWVkIGFuZCBpbmRlZmF0aWdhYmxlIGdlbmVyYXRpb24gb2Yga25vd2xlZGdlLCBleGNlZWRzIHRo" +
                    "ZSBzaG9ydCB2ZWhlbWVuY2Ugb2YgYW55IGNhcm5hbCBwbGVhc3VyZS4=",
            codec.encode(
                text.toByteArray(Charsets.UTF_8)
            )
        )
    }

    @Test
    fun testEncodePushGatewayLabel() {
        val codec = Base64Codec(char62 = '-', char63 = '_')
        assertEquals(
            "RWg_",
            codec.encode("Eh?".toByteArray(Charsets.UTF_8))
        )
        assertEquals(
            "Pj4-",
            codec.encode(">>>".toByteArray(Charsets.UTF_8))
        )
    }
}
