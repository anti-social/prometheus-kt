package dev.evo.prometheus.ktor

import dev.evo.prometheus.PrometheusMetrics

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.accept
import io.ktor.routing.get
import io.ktor.routing.method
import io.ktor.routing.param
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.getOrFail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

import kotlinx.coroutines.delay

@io.ktor.util.KtorExperimentalAPI
class MetricsModuleTests {
    private fun assertContains(content: String, substring: String) {
        assertTrue(
            substring in content,
            "[$substring] substring is not found in [$content]"
        )
    }

    private fun assertNotContains(content: String, substring: String) {
        assertFalse(
            substring in content,
            "[$substring] substring should not be present in [$content]"
        )
    }

    @Test
    fun `metrics module with default metrics`() = withTestApplication({
        metricsModule()
    }) {
        with(handleRequest(HttpMethod.Get, "/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)
            assertContains(content, "# TYPE jvm_threads_current gauge")
            assertContains(content, "# TYPE hiccups histogram")
            assertContains(content, "hiccups_bucket{le=\"+Inf\"} 1.0")
            assertNotContains(content, "http_total_requests")
            assertContains(content, "http_in_flight_requests{method=\"GET\"} 1.0")
        }

        with(handleRequest(HttpMethod.Get, "/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)

            val labels = "method=\"GET\",response_code=\"200\",route=\"/metrics\""
            assertContains(content, "http_total_requests_count{$labels} 1.0")
            assertContains(content, "http_total_requests_sum{$labels} ")
            assertContains(content, "http_total_requests_bucket{$labels,le=\"1.0\"} ")
            assertContains(content, "http_total_requests_bucket{$labels,le=\"2.0\"} ")
            assertContains(content, "http_total_requests_bucket{$labels,le=\"10.0\"} ")
            assertContains(content, "http_total_requests_bucket{$labels,le=\"100.0\"} ")
            assertContains(content, "http_total_requests_bucket{$labels,le=\"1000.0\"} ")
            assertContains(content, "http_total_requests_bucket{$labels,le=\"10000.0\"} 1.0")
            assertContains(content, "http_total_requests_bucket{$labels,le=\"+Inf\"} 1.0")
            assertContains(content, "http_in_flight_requests{method=\"GET\"} 1.0")
        }
    }

    @Test
    fun `custom http metrics`() = withTestApplication({
        class CustomMetrics : PrometheusMetrics(), HttpMetrics {
            override val totalRequests by histogram("request_duration", listOf(100.0, 500.0, 1000.0)) {
                HttpRequestLabels()
            }

            override val metrics: PrometheusMetrics
                get() = this
        }
        val metrics = CustomMetrics()
        metricsModule(MetricsFeature(metrics))
    }) {
        with(handleRequest(HttpMethod.Get, "/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)
            assertNotContains(content, "# TYPE jvm_threads_current gauge")
            assertNotContains(content, "http_in_flight_requests")
            assertNotContains(content, "request_duration_count")
        }
        with(handleRequest(HttpMethod.Get, "/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)
            assertNotContains(content, "# TYPE jvm_threads_current gauge")
            assertNotContains(content, "http_in_flight_requests")
            val labels = "method=\"GET\",response_code=\"200\",route=\"/metrics\""
            assertContains(content, "# TYPE request_duration histogram")
            assertContains(content, "request_duration_count{$labels} 1.0")
            assertContains(content, "request_duration_sum{$labels} ")
            assertNotContains(content, "request_duration_bucket{$labels,le=\"1.0\"}")
            assertNotContains(content, "request_duration_bucket{$labels,le=\"10000.0\"}")
            assertContains(content, "request_duration_bucket{$labels,le=\"100.0\"} 1.0")
            assertContains(content, "request_duration_bucket{$labels,le=\"500.0\"} 1.0")
            assertContains(content, "request_duration_bucket{$labels,le=\"1000.0\"} 1.0")
            assertContains(content, "request_duration_bucket{$labels,le=\"+Inf\"} 1.0")
        }
    }

    @Test
    fun `custom module configuration`() = withTestApplication({
        val metricsFeature = MetricsFeature()
        install(metricsFeature) {
            enablePathLabel = true
        }

        routing {
            get("/hello") {
                call.respondText("Hello")
            }
            put("/slow/{delay}") {
                // TODO: Find out how to use [TestCoroutineScope.advanceTimeBy] instead of delay
                delay(call.parameters.getOrFail<Long>("delay"))
                call.respondText("It was really slooow!")
            }
            route("/nested") {
                metrics(metricsFeature.metrics)
            }
        }
    }) {
        with(handleRequest(HttpMethod.Get, "/hello")) {
            assertEquals(HttpStatusCode.OK, response.status())
        }
        with(handleRequest(HttpMethod.Put, "/slow")) {
            assertEquals(null, response.status())
        }
        with(handleRequest(HttpMethod.Put, "/slow/110")) {
            assertEquals(HttpStatusCode.OK, response.status())
        }
        with(handleRequest(HttpMethod.Get, "/nested/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)

            assertNotContains(content, "route=\"/\"")

            val helloLabels = "method=\"GET\",response_code=\"200\",route=\"/hello\",path=\"/hello\""
            assertContains(content, "http_total_requests_count{$helloLabels} 1.0")
            assertContains(content, "http_total_requests_sum{$helloLabels} ")
            assertContains(content, "http_total_requests_bucket{$helloLabels,le=\"+Inf\"} 1.0")

            val slowLabels = "method=\"PUT\",response_code=\"200\",route=\"/slow/{delay}\",path=\"/slow/110\""
            assertContains(content, "http_total_requests_count{$slowLabels} 1.0")
            assertContains(content, "http_total_requests_sum{$slowLabels} ")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"1.0\"} 0.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"10.0\"} 0.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"100.0\"} 0.0")
            // FIXME: advance time
            // assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"200.0\"} 1.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"1000.0\"} 1.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"2000.0\"} 1.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"10000.0\"} 1.0")
            assertContains(content, "http_total_requests_bucket{$slowLabels,le=\"+Inf\"} 1.0")
        }
    }

    @Test
    fun routing() = withTestApplication({
        val metricsFeature = MetricsFeature()
        install(metricsFeature)

        routing {
            route("/search") {
                method(HttpMethod.Get) {
                    param("q") {
                        accept(ContentType.Application.Json) {
                            get("/") {
                                val q = call.parameters["q"].let {
                                    if (it != null) {
                                        "\"$it\""
                                    } else {
                                        ""
                                    }
                                }
                                call.respondText("""{"q":$q}""", ContentType.Application.Json, HttpStatusCode.OK)
                            }
                        }
                        get("/") {
                            call.respond(HttpStatusCode.OK, "q=${call.parameters["q"] ?: ""}")
                        }
                    }
                }
            }
            get("/") {
                call.respond(HttpStatusCode.OK, "null")
            }
            get("/parameter/{login}") {
                call.respond(HttpStatusCode.OK, "parameter")
            }
            get("/optional/{username?}") {
                call.respond(HttpStatusCode.OK, "optional")
            }
            get("/tailcard/{path...}") {
                call.respond(HttpStatusCode.OK, "tailcard")
            }
            get("/wildcard/*/{username}") {
                call.respond(HttpStatusCode.OK, "wildcard")
            }
            route("/") {
                metrics(metricsFeature.metrics)
            }
        }
    }) {
        with(handleRequest(HttpMethod.Get, "/")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("null", response.content)
        }
        with(handleRequest(HttpMethod.Get, "/search?q=test")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("q=test", response.content)
        }
        with(handleRequest(HttpMethod.Get, "/search?q=test") {
            addHeader("Accept", ContentType.Application.Json.toString())
        }) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("""{"q":"test"}""", response.content)
        }
        with(handleRequest(HttpMethod.Get, "/parameter/login")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("parameter", response.content)
        }
        with(handleRequest(HttpMethod.Get, "/optional")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("optional", response.content)
        }
        with(handleRequest(HttpMethod.Get, "/optional/alex")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("optional", response.content)
        }
        with(handleRequest(HttpMethod.Get, "/tailcard")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("tailcard", response.content)
        }
        with(handleRequest(HttpMethod.Get, "/tailcard/a/b/c")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("tailcard", response.content)
        }
        with(handleRequest(HttpMethod.Get, "/wildcard/abc/aaa")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("wildcard", response.content)
        }

        with(handleRequest(HttpMethod.Get, "/metrics")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content
            assertNotNull(content)

            assertContains(content, """http_in_flight_requests{method="GET"} 1.0""")

            val labels = """method="GET",response_code="200""""

            val rootLabels = """$labels,route="/""""
            assertContains(content, """http_total_requests_bucket{$rootLabels,le="1.0"} """)
            assertContains(content, """http_total_requests_bucket{$rootLabels,le="+Inf"} 1.0""")
            assertContains(content, """http_total_requests_count{$rootLabels} 1.0""")
            assertContains(content, """http_total_requests_sum{$rootLabels} """)

            val searchLabels = """$labels,route="/search""""
            assertContains(content, """http_total_requests_bucket{$searchLabels,le="1.0"} """)
            assertContains(content, """http_total_requests_bucket{$searchLabels,le="+Inf"} 2.0""")
            assertContains(content, """http_total_requests_count{$searchLabels} 2.0""")
            assertContains(content, """http_total_requests_sum{$searchLabels} """)

            val parameterLabels = """$labels,route="/parameter/{login}""""
            assertContains(content, """http_total_requests_bucket{$parameterLabels,le="1.0"} """)
            assertContains(content, """http_total_requests_bucket{$parameterLabels,le="+Inf"} 1.0""")
            assertContains(content, """http_total_requests_count{$parameterLabels} 1.0""")
            assertContains(content, """http_total_requests_sum{$parameterLabels} """)

            val optionalLabels = """$labels,route="/optional/{username?}""""
            assertContains(content, """http_total_requests_bucket{$optionalLabels,le="1.0"} """)
            assertContains(content, """http_total_requests_bucket{$optionalLabels,le="+Inf"} 2.0""")
            assertContains(content, """http_total_requests_count{$optionalLabels} 2.0""")
            assertContains(content, """http_total_requests_sum{$optionalLabels} """)

            val tailcardLabels = """$labels,route="/tailcard/{...}""""
            assertContains(content, """http_total_requests_bucket{$tailcardLabels,le="1.0"} """)
            assertContains(content, """http_total_requests_bucket{$tailcardLabels,le="+Inf"} 2.0""")
            assertContains(content, """http_total_requests_count{$tailcardLabels} 2.0""")
            assertContains(content, """http_total_requests_sum{$tailcardLabels} """)
        }
    }
}
