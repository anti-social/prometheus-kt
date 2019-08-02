@file:JsModule("process")
@file:JsNonModule
package dev.evo.prometheus.nodejs

internal external fun memoryUsage(): MemoryUsage

internal external class MemoryUsage {
    val rss: Int
    val heapTotal: Int
    val heapUsed: Int
    val external: Int
}
