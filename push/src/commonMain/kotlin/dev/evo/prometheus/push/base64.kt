package dev.evo.prometheus.push

internal class Base64Codec(char62: Char = '+', char63: Char = '/') {
    companion object {
        private const val BASE64_MASK = 0b0011_1111
        private const val BASE64_SET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }

    private val base64Table = BASE64_SET + char62 + char63

    fun encode(data: ByteArray): String {
        val mainSize = data.size / 3 * 3
        val tailSize = data.size % 3
        val buf = StringBuilder(((data.size - 1) / 3 + 1) * 4)
        var i = 0
        while (i < mainSize) {
            val chunk = data[i+2].toInt() or
                    (data[i+1].toInt() shl 8) or
                    (data[i].toInt() shl 16)
            buf.append(base64Table[(chunk ushr 18) and BASE64_MASK])
            buf.append(base64Table[(chunk ushr 12) and BASE64_MASK])
            buf.append(base64Table[(chunk ushr 6) and BASE64_MASK])
            buf.append(base64Table[chunk and BASE64_MASK])
            i += 3
        }
        when (tailSize) {
            1 -> {
                val chunk = data[i].toInt() shl 16
                buf.append(base64Table[(chunk ushr 18) and BASE64_MASK])
                buf.append(base64Table[(chunk ushr 12) and BASE64_MASK])
                buf.append("==")
            }
            2 -> {
                val chunk = (data[i+1].toInt() shl 8) or
                        (data[i].toInt() shl 16)
                buf.append(base64Table[(chunk ushr 18) and BASE64_MASK])
                buf.append(base64Table[(chunk ushr 12) and BASE64_MASK])
                buf.append(base64Table[(chunk ushr 6) and BASE64_MASK])
                buf.append('=')
            }
        }
        return buf.toString()
    }
}
