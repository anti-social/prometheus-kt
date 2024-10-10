import org.gradle.api.JavaVersion

object Versions {
    val jvmTargetVersion = JavaVersion.VERSION_1_8

    val kotlin = "2.0.2"
    val grgit = "4.1.1"
    val taskTree = "2.1.1"

    val atomicfu = "0.25.0"
    val kotnlinxCoroutines = "1.6.4"

    val ktor = "3.0.0"

    val grpc = "1.58.0"

    // Benchmarks
    val jmhPlugin = "0.7.1"
    val jmh = "1.37"
    val prometheusSimpleclient = "0.16.0"

    // Samples
    val slf4j = "2.0.9"
}
