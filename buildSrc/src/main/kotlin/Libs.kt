object Libs {
    fun kotlinx(lib: String, version: String): String {
        return "org.jetbrains.kotlinx:${lib}:${version}"
    }

    fun kotlinxCoroutines(module: String): String {
        return kotlinx("kotlinx-coroutines-${module}", Versions.kotnlinxCoroutines)
    }

    fun atomicfu(): String {
        return kotlinx("atomicfu", Versions.atomicfu)
    }

    fun ktor(module: String): String {
        return "io.ktor:ktor-${module}:${Versions.ktor}"
    }
}
