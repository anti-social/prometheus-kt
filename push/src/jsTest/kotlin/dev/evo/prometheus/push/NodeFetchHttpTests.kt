package dev.evo.prometheus.push

import dev.evo.prometheus.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlin.test.Test

class NodeFetchHttpTests {
    @Test
    fun testNodeHttpClient() = runTest {
        val client = HttpClient(Js) {}
        val s = client.get<String>("http://localhost:11192") {
            // Move the following line out of the put to work
            val data = getData()

            println(data.toString())
        }
        println(s)
    }
}
