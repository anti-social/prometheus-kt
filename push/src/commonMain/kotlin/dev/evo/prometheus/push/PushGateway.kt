package dev.evo.prometheus.push

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.writeSamples
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.request.delete
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray

class PushGatewayException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PushGateway(
    val url: Url, client: HttpClient
) {
    constructor(host: String, port: Int, client: HttpClient) : this(Url("http://$host:$port"), client)
    constructor(host: String, client: HttpClient) : this(host, 9091, client)

    private val base64Codec = Base64Codec(char62 = '-', char63 = '_')

    private val client = client.config {
        HttpResponseValidator {
            validateResponse { resp ->
                // Push gateway must always response with 202
                // but we allow all the 2xx statuses
                when (val status = resp.status.value) {
                    !in 200..299 -> {
                        throw PushGatewayException(
                            "Expected status code 202 but was: $status"
                        )
                    }
                }
            }
        }
    }

    suspend fun push(
        metrics: PrometheusMetrics,
        job: String,
        groupingLabels: LabelSet? = null,
        replace: Boolean = true
    ) {
        val samplesWriter = StringBuilder()
        writeSamples(metrics.dump(), samplesWriter)
        client.request {
            method = if (replace) {
                HttpMethod.Put
            } else {
                HttpMethod.Post
            }
            url {
                takeFrom(this@PushGateway.url)
                appendPathSegments(pathComponents(job, groupingLabels))
            }
            setBody(samplesWriter.toString())
        }
    }

    suspend fun delete(job: String, groupingLabels: LabelSet? = null) {
        client.delete {
            method = HttpMethod.Delete
            url {
                takeFrom(this@PushGateway.url)
                appendPathSegments(pathComponents(job, groupingLabels))
            }
        }
    }

    private fun pathComponents(job: String, groupingLabels: LabelSet?): List<String> {
        val pathComponents = mutableListOf(
            "metrics",
            encodeLabel("job", job)
        )
        if (groupingLabels != null) {
            pathComponents.addAll(labelsToPath(groupingLabels))
        }
        return pathComponents
    }

    private fun labelsToPath(labels: LabelSet): Sequence<String> {
        return labels.labels().map { (name, value) ->
            encodeLabel(name, value)
        }
    }

    private fun encodeLabel(name: String, value: String): String {
        return if ("/" in value) {
            "$name@base64/${base64Codec.encode(value.toByteArray(Charsets.UTF_8))}"
        } else {
            "$name/$value"
        }
    }
}
