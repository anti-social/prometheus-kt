package dev.evo.prometheus

enum class Platform {
    JVM, JS, NATIVE
}

expect val platform: Platform
