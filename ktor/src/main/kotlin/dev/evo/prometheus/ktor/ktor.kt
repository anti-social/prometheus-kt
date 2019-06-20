package dev.evo.prometheus.ktor

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.hiccup.HiccupMetrics
import dev.evo.prometheus.jvm.DefaultJvmMetrics
import dev.evo.prometheus.writeSamples

import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.response.respondTextWriter
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.AttributeKey

import kotlin.system.measureNanoTime

fun Application.metricsModule(metrics: PrometheusMetrics? = null, httpMetricsName: String = "http") {
    val mainMetrics = metrics ?: DefaultMetrics().apply {
        hiccups.startTracking(this@metricsModule)
    }

    install(MetricsFeature) {
        this.httpMetrics = mainMetrics.getSubmetrics(httpMetricsName) as StandardHttpMetrics
    }

    routing {
        metrics(mainMetrics)
    }
}

fun Route.metrics(metrics: PrometheusMetrics) {
    get("/metrics") {
        metrics.collect()
        call.respondTextWriter {
            writeSamples(metrics.dump(), this)
        }
    }
}

object MetricsFeature : ApplicationFeature<Application, MetricsFeature.Configuration, Unit> {
    override val key = AttributeKey<Unit>("Response metrics collector")
    private val routeKey = AttributeKey<Route>("Route info")

    class Configuration {
        lateinit var httpMetrics: StandardHttpMetrics
        var enablePathLabel = false
    }

    override fun install(pipeline: Application, configure: Configuration.() -> Unit) {
        val configuration = Configuration().apply(configure)

        pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) {
            it.attributes.put(routeKey, it.route)
        }

        pipeline.intercept(ApplicationCallPipeline.Monitoring) {
            val requestTimeMs = measureNanoTime {
                proceed()
            }.toDouble() / 1_000_000.0

            val method = call.request.httpMethod.value
            val statusCode = call.response.status()?.value
            val route = context.attributes.getOrNull(routeKey)
            val path = call.request.path()
            configuration.httpMetrics.totalRequests.observe(requestTimeMs) {
                this.method = method
                if (statusCode != null) {
                    this.statusCode = statusCode.toString()
                }
                if (route != null) {
                    this.route = route.toString()
                }
                if (configuration.enablePathLabel) {
                    this.path = path
                }
            }
        }
    }
}

class DefaultMetrics : PrometheusMetrics() {
    val jvm by submetrics(DefaultJvmMetrics())
    val hiccups by submetrics("", HiccupMetrics())
    val http by submetrics(StandardHttpMetrics())
}

class StandardHttpMetrics : PrometheusMetrics() {
    val totalRequests by histogram(
            "total_requests",
            scale(1.0) + scale(10.0) + scale(100.0) + listOf(1000.0)
    ) { HttpRequestLabels() }
    // val inFlightRequests by gauge("in_flight_requests") { HttpRequestLabels() }
}

class HttpRequestLabels : LabelSet() {
    var method by label()
    var statusCode by label("response_code")
    var route by label()
    var path by label()
}
