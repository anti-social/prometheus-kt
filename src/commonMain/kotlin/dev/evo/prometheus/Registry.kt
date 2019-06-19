package dev.evo.prometheus

internal expect class Registry<K, V>() {
    val size: Int
    suspend fun getOrPut(key: K, init: () -> V): V
    suspend fun forEach(block: (Pair<K, V>) -> Unit)
}
