import dev.evo.prometheus.ktor.MetricsFeature
import dev.evo.prometheus.ktor.metrics

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail

import kotlinx.coroutines.delay

import kotlin.random.Random

suspend fun main(args: Array<String>) {
    println("Starting application ...")

    val env = commandLineEnvironment(arrayOf("-port=8080") + args)
    val port = env.connectors.single().port
    val url = "localhost:$port"
    println("""
        Try some requests:
        curl -X POST $url/process
        curl -X GET $url/delay/123
        
        And see metrics at: http://localhost:$port/metrics
    """.trimIndent())

    embeddedServer(
        Netty,
        port = port,
        module = Application::module
    )
        .start(wait = true)
}

fun Application.module() {
    val metrics = MetricsFeature()
    install(metrics)

    routing {
        get("/delay/{delayMs}") {
            val delayMs = call.parameters.getOrFail<Long>("delayMs")
            delay(delayMs)
            call.respondText("Delay was: $delayMs")
        }
        post("/process") {
            val delayMs = Random.nextLong(1000L)
            delay(delayMs)
            call.respondText("Processed in $delayMs ms")
        }

        metrics(metrics.metrics)
    }
}
