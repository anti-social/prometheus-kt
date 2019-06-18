package dev.evo.prometheus

import java.util.concurrent.ConcurrentHashMap

internal actual class Registry<K, V> {
    private val registry = ConcurrentHashMap<K, V>()

    actual val size = registry.size

    actual fun getOrPut(key: K, init: () -> V): V {
        return registry.computeIfAbsent(key, { init () })
    }

    actual operator fun iterator(): Iterator<Map.Entry<K, V>> {
        return registry.iterator()
    }
}
