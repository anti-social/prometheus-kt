import dev.evo.prometheus.ktor.MetricsFeature
import dev.evo.prometheus.ktor.metrics

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.getOrFail

import kotlin.random.Random

import kotlinx.coroutines.delay

@io.ktor.util.KtorExperimentalAPI
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

@io.ktor.util.KtorExperimentalAPI
fun Application.module() {
    install(MetricsFeature)

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

        metrics(MetricsFeature.metrics)
    }
}
