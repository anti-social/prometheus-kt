package dev.evo.prometheus

inline fun cycle(block: (Int) -> Unit) {
    var i = 0
    while (i < 1_000_000) {
        block(i)
        i++
    }
}
