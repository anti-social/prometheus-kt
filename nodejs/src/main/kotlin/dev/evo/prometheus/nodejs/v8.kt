@file:JsModule("v8")
@file:JsNonModule
package dev.evo.prometheus.nodejs

internal external fun getHeapSpaceStatistics(): Array<HeapSpaceStatistic>

internal external class HeapSpaceStatistic {
    val space_name: String
    val space_size: Double
    val space_used_size: Double
    val space_available_size: Double
    val physical_space_size: Double
}