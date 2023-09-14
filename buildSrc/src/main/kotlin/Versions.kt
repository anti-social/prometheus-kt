import org.gradle.api.JavaVersion

object Versions {
    val jvmTargetVersion = JavaVersion.VERSION_1_8

    val kotlin = "1.9.0"

    val grgit = "4.1.0"

    val atomicfu = "0.21.0"
    val kotnlinxCoroutines = "1.6.4"

    val ktor = "2.2.2"

    // Use okio because kotlinx-io does not support directory listing
    val okio = "3.5.0"

    // Benchmarks
    val jmhPlugin = "0.7.1"
    val jmh = "1.37"
    val prometheusSimpleclient = "0.16.0"

    // Samples
    val slf4j = "2.0.9"
}
