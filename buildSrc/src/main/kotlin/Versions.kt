import org.gradle.api.JavaVersion

// https://github.com/gradle/gradle/issues/1697#issuecomment-480599718
object Versions {
    val jvmTarget = JavaVersion.VERSION_1_8.toString()

    val kotlin = "1.5.0"

    val grgit = "4.1.0"

    val atomicfu = "0.16.1"
    val kotnlinxCoroutines = "1.5.0-native-mt"

    val ktor = "1.5.3"

    val textEncoding = "0.7.0"

    // Benchmarks
    val jmhPlugin = "0.5.3"
    val jmh = "1.28"
    val prometheusSimpleclient = "0.6.0"
}
