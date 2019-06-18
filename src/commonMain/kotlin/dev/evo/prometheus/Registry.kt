package dev.evo.prometheus

internal expect class Registry<K, V>() {
    val size: Int
    fun getOrPut(key: K, init: () -> V): V
    operator fun iterator(): Iterator<Map.Entry<K, V>>
}
