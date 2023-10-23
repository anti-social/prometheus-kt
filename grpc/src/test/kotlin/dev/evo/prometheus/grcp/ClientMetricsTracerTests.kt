package dev.evo.prometheus.grpc

import dev.evo.prometheus.MetricValue

import io.grpc.Attributes
import io.grpc.ClientStreamTracer
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status

import java.io.InputStream

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClientMetricsTracerTests {
    private val headers = Metadata()
    private val headersWithRetry = Metadata().also {
        it.put(RETRY_HEADER_KEY, "2")
    }
    private val methodMarshaller = object : MethodDescriptor.Marshaller<String> {
        override fun stream(value: String): InputStream {
            return StringInputStream(value)
        }

        override fun parse(stream: InputStream): String {
            return (stream as StringInputStream).value
        }
    }
    private val method: MethodDescriptor<String, String> =
        MethodDescriptor.newBuilder<String, String>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setRequestMarshaller(methodMarshaller)
            .setResponseMarshaller(methodMarshaller)
            .setFullMethodName("pkg.Srv/testMethod")
            .build()

    val streamInfo = ClientStreamTracer.StreamInfo.newBuilder().build()

    companion object {
        private val RETRY_HEADER_KEY = Metadata.Key.of(
            "x-retry",
            Metadata.ASCII_STRING_MARSHALLER
        )
    }

    class StringInputStream(val value: String) : InputStream() {
        override fun read(): Int {
            throw UnsupportedOperationException("Should not be called")
        }
    }

    @Test
    fun default() = runTest {
        val metrics = GrpcClientMetrics()
        val timeSource = TestTimeSource()
        val tracerFactory = ClientMetricsTracer.Factory(method, metrics, this, timeSource)
        val tracer = tracerFactory.newClientStreamTracer(streamInfo, headers)
        val requestLabels = GrpcRequestLabels().also {
            it.service = "pkg.Srv"
            it.method = "testMethod"
            it.type = "UNARY"
        }
        val responseLabels = GrpcResponseLabels().also {
            it.service = "pkg.Srv"
            it.method = "testMethod"
            it.type = "UNARY"
            it.code = "OK"
        }

        tracer.streamCreated(Attributes.EMPTY, headers)
        tracer.outboundMessage(0)
        tracer.outboundMessageSent(0, 123, -1)
        tracer.flush()

        assertEquals(
            1L,
            metrics.startedTotal.getMetricData(requestLabels),
        )
        assertEquals(
            null,
            metrics.handledTotal.getMetricData(responseLabels),
        )
        assertEquals(
            1L,
            metrics.msgSentTotal.getMetricData(requestLabels),
        )
        assertEquals(
            123L,
            metrics.bytesSent.getMetricData(requestLabels),
        )
        assertEquals(
            null,
            metrics.msgReceivedTotal.getMetricData(requestLabels),
        )
        assertEquals(
            null,
            metrics.bytesReceived.getMetricData(requestLabels),
        )
        assertEquals(
            null,
            metrics.handledLatencySeconds.getMetricData(responseLabels),
        )

        timeSource += 8.milliseconds
        tracer.inboundMessage(0)
        tracer.inboundMessageRead(0, 456, -1)
        tracer.streamClosed(Status.OK)
        tracer.await()

        assertEquals(
            1L,
            metrics.startedTotal.getMetricData(requestLabels),
        )
        assertEquals(
            1L,
            metrics.handledTotal.getMetricData(responseLabels),
        )
        assertEquals(
            1L,
            metrics.msgSentTotal.getMetricData(requestLabels),
        )
        assertEquals(
            123L,
            metrics.bytesSent.getMetricData(requestLabels),
        )
        assertEquals(
            1L,
            metrics.msgReceivedTotal.getMetricData(requestLabels),
        )
        assertEquals(
            456L,
            metrics.bytesReceived.getMetricData(requestLabels),
        )
        assertEquals(
            MetricValue.Histogram.Data(
                1L,
                0.008,
                LongArray(38) { ix ->
                    when (ix) {
                        7 -> 1L
                        else -> 0L
                    }
                }
            ),
            metrics.handledLatencySeconds.getMetricData(responseLabels)
        )
    }

    class CustomGrpcRequestLabels : GrpcRequestLabels() {
        var retry by label("grpc_retry")

        override fun populate(methodDescriptor: MethodDescriptor<*, *>, headers: Metadata) {
            super.populate(methodDescriptor, headers)
            retry = headers.get(RETRY_HEADER_KEY)
        }
    }

    class CustomGrpcResponseLabels : GrpcResponseLabels() {
        var retry by label("grpc_retry")

        override fun populate(
            methodDescriptor: MethodDescriptor<*, *>,
            headers: Metadata,
            status: Status
        ) {
            super.populate(methodDescriptor, headers, status)
            retry = headers.get(RETRY_HEADER_KEY)
        }
    }

    @Test
    fun withCustomLabels() = runTest {
        val metrics = GrpcClientMetrics(::CustomGrpcRequestLabels, ::CustomGrpcResponseLabels)
        val timeSource = TestTimeSource()
        val tracerFactory = ClientMetricsTracer.Factory(method, metrics, this, timeSource)
        val tracer = tracerFactory.newClientStreamTracer(streamInfo, headersWithRetry)
        val requestLabels = CustomGrpcRequestLabels().also {
            it.service = "pkg.Srv"
            it.method = "testMethod"
            it.type = "UNARY"
            it.retry = "2"
        }
        val responseLabels = CustomGrpcResponseLabels().also {
            it.service = "pkg.Srv"
            it.method = "testMethod"
            it.type = "UNARY"
            it.code = "OK"
            it.retry = "2"
        }

        tracer.streamCreated(Attributes.EMPTY, headersWithRetry)
        tracer.outboundMessage(0)
        tracer.outboundMessageSent(0, 123, -1)
        tracer.flush()

        assertEquals(
            1L,
            metrics.startedTotal.getMetricData(requestLabels),
        )
        assertEquals(
            null,
            metrics.handledTotal.getMetricData(responseLabels),
        )
        assertEquals(
            1L,
            metrics.msgSentTotal.getMetricData(requestLabels),
        )
        assertEquals(
            123L,
            metrics.bytesSent.getMetricData(requestLabels),
        )
        assertEquals(
            null,
            metrics.msgReceivedTotal.getMetricData(requestLabels),
        )
        assertEquals(
            null,
            metrics.bytesReceived.getMetricData(requestLabels),
        )
        assertEquals(
            null,
            metrics.handledLatencySeconds.getMetricData(responseLabels),
        )

        timeSource += 8.milliseconds
        tracer.inboundMessage(0)
        tracer.inboundMessageRead(0, 456, -1)
        tracer.streamClosed(Status.OK)
        tracer.await()

        assertEquals(
            1L,
            metrics.startedTotal.getMetricData(requestLabels),
        )
        assertEquals(
            1L,
            metrics.handledTotal.getMetricData(responseLabels),
        )
        assertEquals(
            1L,
            metrics.msgReceivedTotal.getMetricData(requestLabels),
        )
        assertEquals(
            123L,
            metrics.bytesSent.getMetricData(requestLabels),
        )
        assertEquals(
            1L,
            metrics.msgSentTotal.getMetricData(requestLabels),
        )
        assertEquals(
            456L,
            metrics.bytesReceived.getMetricData(requestLabels),
        )
        assertEquals(
            MetricValue.Histogram.Data(
                1L,
                0.008,
                LongArray(38) { ix ->
                    when (ix) {
                        7 -> 1L
                        else -> 0L
                    }
                }
            ),
            metrics.handledLatencySeconds.getMetricData(responseLabels)
        )
    }
}
