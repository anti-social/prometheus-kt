import org.gradle.api.JavaVersion

// https://github.com/gradle/gradle/issues/1697#issuecomment-480599718
object Versions {
    val kotlin = "1.3.40"
    val jvmTarget = JavaVersion.VERSION_1_8.toString()

    val grgit = "3.1.1"

    val atomicfu = "0.12.8"
    val kotnlinxCoroutines = "1.3.0-RC"

    val ktor = "1.2.2"

    // Benchmarks
    val jmhPlugin = "0.4.8"
    val jmh = "1.21"
    val prometheusSimpleclient = "0.6.0"
}
