package dev.evo.prometheus.ktor

import dev.evo.prometheus.GaugeLong
import dev.evo.prometheus.Histogram
import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.hiccup.HiccupMetrics
import dev.evo.prometheus.jvm.DefaultJvmMetrics
import dev.evo.prometheus.writeSamples

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
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

fun <TMetrics: PrometheusMetrics> Application.metricsModule(
    metricsFeature: MetricsFeature<TMetrics>? = null
) {
    val feature = metricsFeature
        ?: MetricsFeature.also {
            it.metrics.hiccups.startTracking(this@metricsModule)
        }

    install(feature) {
        feature.configure(this)
    }

    routing {
        metrics(feature.metrics)
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

abstract class MetricsFeature<TMetrics: PrometheusMetrics>(val metrics: TMetrics):
    ApplicationFeature<Application, MetricsFeature.Configuration, Unit>
{
    override val key = AttributeKey<Unit>("Response metrics collector")
    private val routeKey = AttributeKey<Route>("Route info")

    class Configuration {
        var totalRequests: Histogram<HttpRequestLabels>? = null
        var inFlightRequests: GaugeLong<HttpRequestLabels>? = null
        var enablePathLabel = false
    }

    companion object Default : MetricsFeature<DefaultMetrics>(DefaultMetrics()) {
        override fun configure(configuration: Configuration) {
            configuration.totalRequests = metrics.http.totalRequests
            configuration.inFlightRequests = metrics.http.inFlightRequests
        }
    }

    abstract fun configure(configuration: Configuration)

    override fun install(pipeline: Application, configure: Configuration.() -> Unit) {
        val configuration = Configuration().apply(configure)

        pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) { call ->
            call.attributes.put(routeKey, call.route)
        }

        pipeline.intercept(ApplicationCallPipeline.Monitoring) {
            val requestTimeMs = measureNanoTime {
                configuration.inFlightRequests?.incAndDec({
                    fromCall(call, configuration.enablePathLabel)
                }) {
                    proceed()
                } ?: proceed()
            }.toDouble() / 1_000_000.0

            configuration.totalRequests?.observe(requestTimeMs) {
                fromCall(call, configuration.enablePathLabel)
            }
        }
    }

    private fun HttpRequestLabels.fromCall(call: ApplicationCall, enablePathLabel: Boolean) {
        method = call.request.httpMethod.value
        statusCode = call.response.status()?.value?.toString()
        route = call.attributes.getOrNull(routeKey)?.toString()
        if (enablePathLabel) {
            path = call.request.path()
        }
    }
}

class DefaultMetrics : PrometheusMetrics() {
    val jvm by submetrics(DefaultJvmMetrics())
    val hiccups by submetrics(HiccupMetrics())
    val http by submetrics(StandardHttpMetrics())
}

class StandardHttpMetrics : PrometheusMetrics() {
    private val prefix = "http"

    val totalRequests by histogram(
            "${prefix}_total_requests", logScale(0, 3)
    ) { HttpRequestLabels() }
    val inFlightRequests by gaugeLong("${prefix}_in_flight_requests") { HttpRequestLabels() }
}

class HttpRequestLabels : LabelSet() {
    var method by label()
    var statusCode by label("response_code")
    var route by label()
    var path by label()
}
