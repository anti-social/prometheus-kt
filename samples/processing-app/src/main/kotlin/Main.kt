import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.jvm.DefaultJvmMetrics
import dev.evo.prometheus.ktor.MetricsFeature
import dev.evo.prometheus.ktor.metricsModule

import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import kotlin.random.Random

import kotlinx.coroutines.delay

suspend fun main(args: Array<String>) {
    println("Starting application ...")

    val env = commandLineEnvironment(arrayOf("-port=9090") + args)
    val port = env.connectors.single().port
    println("See metrics at: http://localhost:$port/metrics")

    val metricsApp = embeddedServer(
        Netty,
        port = port,
        module = {
            // metricsModule(MetricsFeature(AppMetrics))
            metricsModule(AppMetrics)
        }
    )
        .start(wait = false)

    startProcessing()

    metricsApp.stop(1000, 2000)
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