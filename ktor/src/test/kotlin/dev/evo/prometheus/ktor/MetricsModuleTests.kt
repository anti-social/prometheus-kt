package dev.evo.prometheus.ktor

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.hiccup.MEASURES_ARRAY_SIZE
import dev.evo.prometheus.hiccup.DEFAULT_DELAY_INTERVAL

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.accept
import io.ktor.server.routing.get
import io.ktor.server.routing.method
import io.ktor.server.routing.param
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.testApplication
import io.ktor.server.util.getOrFail

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.testTimeSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope

import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@kotlinx.coroutines.ExperimentalCoroutinesApi
class MetricsModuleTests {

    private class TestScope : CoroutineScope {
        val lastUncaughtException = atomic<Throwable?>(null)
        val exceptionHandler = CoroutineExceptionHandler { _, exc ->
            lastUncaughtException.value = exc
        }
        val testScheduler = TestCoroutineScheduler()
        override val coroutineContext: CoroutineContext =
            StandardTestDispatcher(testScheduler) + exceptionHandler

        fun checkException() {
            assertNull(lastUncaughtException.value)
        }
    }

    private suspend fun withTestScope(block: suspend TestScope.() -> Unit) {
        val scope = TestScope()
        scope.block()
    }

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
    fun `metrics module with default metrics`() = testApplication {
        // TODO: Found out how to use `runTest` with `testApplication`
        withTestScope {
            application {
                metricsModule(
                    coroutineScope = this@withTestScope,
                )
            }
            startApplication()

            repeat(MEASURES_ARRAY_SIZE - 1) {
                testScheduler.advanceTimeBy(DEFAULT_DELAY_INTERVAL)
                testScheduler.runCurrent()
                assertNull(lastUncaughtException.value)
            }

            client.get("/metrics").let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                val content = response.bodyAsText()
                assertNotNull(content)

                assertContains(content, "# TYPE jvm_threads_current gauge")

                assertNotContains(content, "# TYPE hiccups histogram")

                assertNotContains(content, "# TYPE http_total_requests histogram")

                assertContains(content, "http_in_flight_requests{method=\"GET\"} 1.0")
            }

            testScheduler.advanceTimeBy(DEFAULT_DELAY_INTERVAL)
            testScheduler.runCurrent()
            assertNull(lastUncaughtException.value)

            client.get("/metrics").let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                val content = response.bodyAsText()
                assertNotNull(content)

                assertContains(content, "# TYPE jvm_threads_current gauge")

                assertContains(content, "# TYPE hiccups histogram")
                assertContains(content, "hiccups_bucket{le=\"5.0\"} 0.0")
                assertContains(content, "hiccups_bucket{le=\"+Inf\"} 100.")

                val labels = "method=\"GET\",response_code=\"200\",route=\"/metrics\""
                assertContains(content, "# TYPE http_total_requests histogram")
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

                assertContains(content, "http_in_flight_requests{method=\"GET\"} 1.0")

            }

            repeat(MEASURES_ARRAY_SIZE) {
                testScheduler.advanceTimeBy(DEFAULT_DELAY_INTERVAL)
                testScheduler.runCurrent()
                assertNull(lastUncaughtException.value)
            }

            client.get("/metrics").let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                val content = response.bodyAsText()
                assertNotNull(content)

                assertContains(content, "# TYPE hiccups histogram")
                assertContains(content, "hiccups_bucket{le=\"+Inf\"} 200.0")
            }
        }
    }

    @Test
    fun `metrics module with default metrics and disabled hiccups`() = testApplication {
        withTestScope {
            application {
                metricsModule(
                    startHiccups = false,
                    coroutineScope = this@withTestScope,
                )
            }

            client.get("/metrics").let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                val content = response.bodyAsText()
                assertNotNull(content)
                assertContains(content, "# TYPE jvm_threads_current gauge")
                assertNotContains(content, "# TYPE hiccups histogram")
                assertContains(content, "http_in_flight_requests{method=\"GET\"} 1.0")
            }
        }
    }

    @Test
    fun `custom metrics`() = testApplication {
        class TaskLables : LabelSet() {
            var source by label("source")
        }
        class TaskMetrics : PrometheusMetrics() {
            val processedCount by counter("processed_count", labelsFactory = ::TaskLables)
            val processedTime by counter("processed_time", labelsFactory = ::TaskLables)
        }

        val metrics = TaskMetrics()
        application {
            metricsModule(metrics)
        }

        metrics.processedCount.add(2.0) { source = "kafka" }
        metrics.processedTime.add(133.86) { source = "kafka" }

        client.get("/metrics").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val content = response.bodyAsText()
            assertNotNull(content)
            assertNotContains(content, "# TYPE jvm_threads_current gauge")
            val labels = "source=\"kafka\""
            assertContains(content, "processed_count{$labels} 2.0")
            assertContains(content, "processed_time{$labels} 133.86")
        }
    }

    @Test
    fun `custom http metrics`() = testApplication {
        class CustomMetrics : PrometheusMetrics(), HttpMetrics {
            override val totalRequests by histogram("request_duration", listOf(100.0, 500.0, 1000.0)) {
                HttpRequestLabels()
            }

            override val metrics: PrometheusMetrics
                get() = this
        }
        val metrics = CustomMetrics()

        application {
            metricsModule(MetricsFeature(metrics))
        }

        client.get("/metrics").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val content = response.bodyAsText()
            assertNotNull(content)
            assertNotContains(content, "# TYPE jvm_threads_current gauge")
            assertNotContains(content, "http_in_flight_requests")
            assertNotContains(content, "request_duration_count")
        }

        client.get("/metrics").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val content = response.bodyAsText()
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
    fun `custom module configuration`() = testApplication {
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

        client.get("/hello").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        client.put("/slow").let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        client.put("/slow/110").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        client.get("/nested/metrics").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val content = response.bodyAsText()
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
    fun routing() = testApplication {
        val metricsFeature = MetricsFeature()
        install(metricsFeature)

        routing {
            route("/search") {
                method(HttpMethod.Get) {
                    param("q") {
                        get("") {
                            call.respond(HttpStatusCode.OK, "q=${call.parameters["q"] ?: ""}")
                        }
                        accept(ContentType.Application.Json) {
                            get("") {
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

        client.get("/").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("null", response.bodyAsText())
        }

        client.get("/search?q=test") {
            headers {
                append("Accept", ContentType.Text.Html.toString())
            }
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("q=test", response.bodyAsText())
        }

        client.get("/search?q=test") {
            headers {
                append("Accept", ContentType.Application.Json.toString())
            }
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"q":"test"}""", response.bodyAsText())
        }

        client.get("/parameter/login").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("parameter", response.bodyAsText())
        }

        client.get("/optional").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("optional", response.bodyAsText())
        }

        client.get("/optional/alex").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("optional", response.bodyAsText())
        }

        client.get("/tailcard").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("tailcard", response.bodyAsText())
        }

        client.get("/tailcard/a/b/c").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("tailcard", response.bodyAsText())
        }

        client.get("/wildcard/abc/aaa").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("wildcard", response.bodyAsText())
        }

        client.get("/metrics").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val content = response.bodyAsText()
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
