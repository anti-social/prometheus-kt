package dev.evo.prometheus.push

import dev.evo.prometheus.runTest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.put
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals

suspend fun getData(): Pair<String, String> {
    delay(10)
    return "Hello" to "world"
}

// class NodeHttpTests {
//     @Test
//     fun testHttpClient() = runTest {
//         val client = HttpClient(MockEngine) {
//             engine {
//                 addHandler { request ->
//                     assertEquals("example.com", request.url.host)
//                     assertEquals("(Hello, world)", (request.body as TextContent).text)
//                     respond("")
//                 }
//             }
//         }
//
//         client.put("http://example.com") {
//             // Move the following line out of the put to work
//             val data = getData()
//
//             body = TextContent(data.toString(), ContentType.Text.Plain)
//         }
//     }
// }
