package dev.evo.prometheus.nodejs

// import dev.evo.prometheus.LabelSet
// import dev.evo.prometheus.PrometheusMetrics
//
// class HeapSpaceLabels : LabelSet() {
//     var space by label()
// }
//
// class NodeHeapSpaceMetrics : PrometheusMetrics() {
//     private val prefix = "nodejs_heap_space_size"
//
//     val used by gauge("${prefix}_used") { HeapSpaceLabels() }
//     val total by gauge("${prefix}_total") { HeapSpaceLabels() }
//     val available by gauge("${prefix}_available") { HeapSpaceLabels() }
//
//     override suspend fun collect() {
//         for (heapStats in getHeapSpaceStatistics()) {
//             val trimSuffix = "_space"
//             val spaceName = if (heapStats.space_name.endsWith(trimSuffix)) {
//                 heapStats.space_name.substring(0..(heapStats.space_name.length - trimSuffix.length))
//             } else {
//                 heapStats.space_name
//             }
//             used.set(heapStats.space_used_size) {
//                 space = spaceName
//             }
//             total.set(heapStats.space_size) {
//                 space = spaceName
//             }
//             available.set(heapStats.space_available_size) {
//                 space = spaceName
//             }
//         }
//     }
// }
//
// class DefaultNodeMetrics : PrometheusMetrics() {
//     val heap by submetrics(NodeHeapSpaceMetrics())
// }
