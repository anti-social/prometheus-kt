package dev.evo.prometheus.ktor

import dev.evo.prometheus.GaugeLong
import dev.evo.prometheus.Histogram
import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.hiccup.HiccupMetrics
import dev.evo.prometheus.jvm.DefaultJvmMetrics
import dev.evo.prometheus.writeSamples

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.PathSegmentOptionalParameterRouteSelector
import io.ktor.server.routing.PathSegmentParameterRouteSelector
import io.ktor.server.routing.PathSegmentTailcardRouteSelector
import io.ktor.server.routing.PathSegmentWildcardRouteSelector
import io.ktor.server.routing.Routing
import io.ktor.util.AttributeKey

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.measureNanoTime
import kotlinx.coroutines.CoroutineScope

// TODO: Possibly it is worth to wrap hiccups settings into a config class
fun Application.metricsModule(
    startHiccups: Boolean = true,
    hiccupsCoroutineScope: CoroutineScope? = null,
    hiccupsCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) {
    val feature = MetricsFeature()
    if (startHiccups) {
        feature.metrics.hiccups.startTracking(
            hiccupsCoroutineScope ?: this@metricsModule,
            coroutineContext = hiccupsCoroutineContext,
         )
    }

    metricsModule(feature)
}

fun Application.metricsModule(metrics: PrometheusMetrics) {
    metricsModule(MetricsFeature(metrics))
}

fun <TMetrics: HttpMetrics> Application.metricsModule(
    metricsFeature: MetricsFeature<TMetrics>
) {
    install(metricsFeature)

    routing {
        metrics(metricsFeature.metrics.metrics)
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

open class MetricsFeature<TMetrics: HttpMetrics>(val metrics: TMetrics):
    BaseApplicationPlugin<Application, MetricsFeature.Configuration, Unit>
{
    override val key = AttributeKey<Unit>("Response metrics collector")
    private val routeKey = AttributeKey<Route>("Route info")

    companion object {
        operator fun invoke(): MetricsFeature<DefaultMetrics> {
            return MetricsFeature(DefaultMetrics())
        }

        operator fun invoke(prometheusMetrics: PrometheusMetrics): MetricsFeature<DummyMetrics> {
            return MetricsFeature(DummyMetrics(prometheusMetrics))
        }
    }

    class Configuration {
        var totalRequests: Histogram<HttpRequestLabels>? = null
        var inFlightRequests: GaugeLong<HttpRequestLabels>? = null
        var enablePathLabel = false
    }

    open fun defaultConfiguration(): Configuration {
        return Configuration().apply {
            totalRequests = metrics.totalRequests
            inFlightRequests = metrics.inFlightRequests
        }
    }

    override fun install(pipeline: Application, configure: Configuration.() -> Unit) {
        val configuration = defaultConfiguration().apply(configure)

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
        method = call.request.httpMethod
        statusCode = call.response.status()
        route = call.attributes.getOrNull(routeKey)
        if (enablePathLabel) {
            path = call.request.path()
        }
    }
}

interface HttpMetrics {
    val totalRequests: Histogram<HttpRequestLabels>?
        get() = null
    val inFlightRequests: GaugeLong<HttpRequestLabels>?
        get() = null

    val metrics: PrometheusMetrics
}

class DefaultMetrics : PrometheusMetrics(), HttpMetrics {
    val jvm by submetrics(DefaultJvmMetrics())
    val hiccups by submetrics(HiccupMetrics())
    val http by submetrics(StandardHttpMetrics())

    override val totalRequests: Histogram<HttpRequestLabels>?
        get() = http.totalRequests
    override val inFlightRequests: GaugeLong<HttpRequestLabels>?
        get() = http.inFlightRequests

    override val metrics: PrometheusMetrics
        get() = this
}

class DummyMetrics(private val prometheusMetrics: PrometheusMetrics) : HttpMetrics {
    override val metrics: PrometheusMetrics
        get() = prometheusMetrics
}

class StandardHttpMetrics : PrometheusMetrics() {
    private val prefix = "http"

    val totalRequests by histogram(
            "${prefix}_total_requests", logScale(0, 3)
    ) { HttpRequestLabels() }
    val inFlightRequests by gaugeLong("${prefix}_in_flight_requests") {
        HttpRequestLabels()
    }
}

class HttpRequestLabels : LabelSet() {
    var method: HttpMethod? by label { value }
    var statusCode: HttpStatusCode? by label("response_code") { value.toString() }
    var route: Route? by label {
        toLabelString()
    }
    var path by label()

    fun Route.toLabelString(): String {
        val segment = when (selector) {
            is PathSegmentConstantRouteSelector -> selector
            is PathSegmentParameterRouteSelector -> selector
            is PathSegmentOptionalParameterRouteSelector -> selector
            is PathSegmentTailcardRouteSelector -> selector
            is PathSegmentWildcardRouteSelector -> selector
            else -> null
        }

        val parent = parent
        return when {
            segment == null -> parent?.toLabelString() ?: "/"
            parent == null -> "/$segment"
            else -> {
                val parentSegment = parent.toLabelString()
                when {
                    parentSegment.isEmpty() -> segment.toString()
                    parentSegment.endsWith('/') -> "$parentSegment$segment"
                    else -> "$parentSegment/$segment"
                }
            }
        }
    }
}
