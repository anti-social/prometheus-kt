import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.jvm.DefaultJvmMetrics
import dev.evo.prometheus.ktor.MetricsFeature
import dev.evo.prometheus.ktor.metricsModule

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import java.util.concurrent.TimeUnit

import kotlin.random.Random

import kotlinx.coroutines.delay

suspend fun main(args: Array<String>) {
    println("Starting application ...")

    val metricsApp = embeddedServer(
        Netty,
        port = 9090,
        module = {
            metricsModule(MetricsFeature<AppMetrics>(AppMetrics))
        }
    )
        .start(wait = false)

    startProcessing()

    metricsApp.stop(1000, 2000, TimeUnit.MILLISECONDS)
}

suspend fun startProcessing() {
    while (true) {
        AppMetrics.processedProducts.measureTime({
            source = when (Random.nextInt(3)) {
                0 -> "manual"
                else -> "app"
            }
        }) {
            delay(Random.nextLong(1000L))
        }
    }
}

class ProcessingLabels : LabelSet() {
    var source by label()
}

object AppMetrics : PrometheusMetrics() {
    val processedProducts by histogram("processed_products", logScale(0, 2)) {
        ProcessingLabels()
    }
    val jvm by submetrics(DefaultJvmMetrics())
}