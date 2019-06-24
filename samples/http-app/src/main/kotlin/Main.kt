import dev.evo.prometheus.ktor.MetricsFeature
import dev.evo.prometheus.ktor.metrics

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.getOrFail

import kotlin.random.Random

import kotlinx.coroutines.delay

@io.ktor.util.KtorExperimentalAPI
suspend fun main(args: Array<String>) {
    println("Starting application ...")

    embeddedServer(
        Netty,
        port = 8088,
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
