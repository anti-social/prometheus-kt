import org.gradle.api.JavaVersion

// https://github.com/gradle/gradle/issues/1697#issuecomment-480599718
object Versions {
    val jvmTarget = JavaVersion.VERSION_1_8.toString()

    val kotlin = "1.6.21"

    val grgit = "4.1.0"

    val atomicfu = "0.16.3"
    val kotnlinxCoroutines = "1.5.2-native-mt"

    val ktor = "1.6.4"

    val textEncoding = "0.7.0"

    // Benchmarks
    val jmhPlugin = "0.5.3"
    val jmh = "1.28"
    val prometheusSimpleclient = "0.6.0"
}
