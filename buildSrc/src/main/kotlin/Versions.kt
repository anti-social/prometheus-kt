import org.gradle.api.JavaVersion

object Versions {
    val jvmTargetVersion = JavaVersion.VERSION_11

    val kotlin = "2.0.0"
    val grgit = "4.1.1"
    val taskTree = "2.1.1"

    val atomicfu = "0.25.0"
    val kotnlinxCoroutines = "1.9.0"

    val ktor = "3.0.0"

    val grpc = "1.67.1"

    // Benchmarks
    val jmhPlugin = "0.7.1"
    val jmh = "1.37"
    val prometheusSimpleclient = "0.16.0"

    // Samples
    val slf4j = "2.0.9"
}
