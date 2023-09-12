import org.gradle.api.JavaVersion

// https://github.com/gradle/gradle/issues/1697#issuecomment-480599718
object Versions {
    val jvmTarget = JavaVersion.VERSION_1_8.toString()

    val kotlin = "1.8.22"

    val grgit = "4.1.0"

    val atomicfu = "0.21.0"
    val kotnlinxCoroutines = "1.6.4"

    val ktor = "2.2.2"

    // Benchmarks
    val jmhPlugin = "0.5.3"
    val jmh = "1.28"
    val prometheusSimpleclient = "0.6.0"

    // Samples
    val slf4j = "1.7.36"
}
