import org.gradle.api.JavaVersion

// https://github.com/gradle/gradle/issues/1697#issuecomment-480599718
object Versions {
    val kotlin = "1.4.21"
    val jvmTarget = JavaVersion.VERSION_1_8.toString()

    val grgit = "3.1.1"

    val atomicfu = "0.14.4"
    val kotnlinxCoroutines = "1.4.2-native-mt"

    val ktor = "1.4.1"

    val textEncoding = "0.7.0"

    // Benchmarks
    val jmhPlugin = "0.4.8"
    val jmh = "1.21"
    val prometheusSimpleclient = "0.6.0"
}
