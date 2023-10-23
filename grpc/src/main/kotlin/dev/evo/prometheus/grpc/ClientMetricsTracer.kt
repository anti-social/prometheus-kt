package dev.evo.prometheus.grpc

import dev.evo.prometheus.PrometheusMetrics

import io.grpc.Attributes
import io.grpc.CallOptions
import io.grpc.Channel as GrpcChannel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ClientStreamTracer
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel

class GrpcClientMetrics<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
    val requestLabelsFactory: () -> LReq,
    val responseLabelsFactory: () -> LResp,
) : PrometheusMetrics() {
    val startedTotal by gaugeLong(
        "grpc_client_started_total",
        help = "Total number of requests started on the client",
        labelsFactory = requestLabelsFactory,
    )
    val handledTotal by gaugeLong(
        "grpc_client_handled_total",
        help = "Total number of requests complited by the client",
        labelsFactory = responseLabelsFactory,
    )

    val msgSentTotal by counterLong(
        "grpc_client_msg_sent_total",
        help = "Total number of sent stream messages",
        labelsFactory = requestLabelsFactory,
    )
    val msgReceivedTotal by counterLong(
        "grpc_client_msg_received_total",
        help = "Total number of received stream messages",
        labelsFactory = requestLabelsFactory,
    )

    val bytesSent by counterLong(
        "grpc_client_bytes_sent",
        help = "Total inbound messages size",
        labelsFactory = requestLabelsFactory,
    )
    val bytesReceived by counterLong(
        "grpc_client_bytes_received",
        help = "Total outbound messages size",
        labelsFactory = requestLabelsFactory
    )

    val handledLatencySeconds by histogram(
        "grpc_client_handled_latency_seconds", logScale(-3, 0),
        help = "Histogram of processing requests latency (seconds)",
        labelsFactory = responseLabelsFactory,
    )
}

fun GrpcClientMetrics(): GrpcClientMetrics<GrpcRequestLabels, GrpcResponseLabels> {
    return GrpcClientMetrics(::GrpcRequestLabels, ::GrpcResponseLabels)
}

class ClientMetricsTracer<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
    private val method: MethodDescriptor<*, *>,
    private val metrics: GrpcClientMetrics<LReq, LResp>,
    private val headers: Metadata,
    private val coroutineScope: CoroutineScope,
    private val timeSource: TimeSource
) : ClientStreamTracer() {

    class Factory<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
        private val method: MethodDescriptor<*, *>,
        private val metrics: GrpcClientMetrics<LReq, LResp>,
        private val coroutineScope: CoroutineScope,
        private val timeSource: TimeSource = TimeSource.Monotonic
    ) : ClientStreamTracer.Factory() {
        override fun newClientStreamTracer(
            info: ClientStreamTracer.StreamInfo,
            headers: Metadata
        ): ClientMetricsTracer<LReq, LResp> {
            return ClientMetricsTracer(method, metrics, headers, coroutineScope, timeSource)
        }
    }

    private sealed class Event {
        object CallStarted : Event()
        class OutboundMessageSent(val wireSize: Long) : Event()
        class InboundMessageRead(val wireSize: Long) : Event()
        class StreamClosed(val status: Status, val duration: Duration?) : Event()
        class Flush(val result: CompletableDeferred<Unit>) : Event()
    }

    private val events = Channel<Event>(4)

    @Volatile
    private lateinit var callStartMark: TimeMark

    private val job = coroutineScope.async {
        lateinit var requestLabels: LReq

        while (true) {
            val eventResult = events.receiveCatching()
            if (eventResult.isClosed) {
                break
            }
            when (val event = eventResult.getOrThrow()) {
                Event.CallStarted -> {
                    requestLabels = metrics.requestLabelsFactory().apply {
                        populate(method, headers)
                    }
                    metrics.startedTotal
                        .getOrCreateMetricValue(requestLabels)
                        .inc()
                }
                is Event.StreamClosed -> {
                    val responseLabels = metrics.responseLabelsFactory().apply {
                        populate(method, headers, event.status)
                    }
                    val duration = event.duration
                    if (duration != null) {
                        val durationSecs = event.duration.inWholeMilliseconds.toDouble() / 1_000
                        val handledLatencyMetric = metrics.handledLatencySeconds.getOrCreateMetricValue(responseLabels)
                        val bucketIx = handledLatencyMetric.findBucketIx(durationSecs)
                        handledLatencyMetric.observe(bucketIx, durationSecs)
                    }

                    metrics.handledTotal.getOrCreateMetricValue(responseLabels).inc()
                }
                is Event.InboundMessageRead -> {
                    metrics.msgReceivedTotal
                        .getOrCreateMetricValue(requestLabels)
                        .add(1)
                    if (event.wireSize > 0) {
                        metrics.bytesReceived
                            .getOrCreateMetricValue(requestLabels)
                            .add(event.wireSize)
                    }
                }
                is Event.OutboundMessageSent -> {
                    metrics.msgSentTotal
                        .getOrCreateMetricValue(requestLabels)
                        .add(1)
                    if (event.wireSize > 0) {
                        metrics.bytesSent
                            .getOrCreateMetricValue(requestLabels)
                            .add(event.wireSize)
                    }
                }
                is Event.Flush -> {
                    event.result.complete(Unit)
                }
            }
        }
    }

    override fun streamCreated(transportAttrs: Attributes, headers: Metadata) {
        callStartMark = timeSource.markNow()
        events.trySend(Event.CallStarted)
    }

    override fun outboundMessageSent(seqNo: Int, optionalWireSize: Long, optionalUncompressedSize: Long) {
        events.trySend(Event.OutboundMessageSent(optionalWireSize))
    }

    override fun inboundMessageRead(seqNo: Int, optionalWireSize: Long, optionalUncompressedSize: Long) {
        events.trySend(Event.InboundMessageRead(optionalWireSize))
    }

    override fun streamClosed(status: Status) {
        events.trySend(Event.StreamClosed(status, callStartMark.elapsedNow()))
        events.close()
    }

    internal suspend fun await() {
        job.await()
    }

    internal suspend fun flush() {
        val result = CompletableDeferred<Unit>()
        events.send(Event.Flush(result))
        result.await()
    }
}

class ClientMetricsInterceptor<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
    private val metrics: GrpcClientMetrics<LReq, LResp>,
    private val coroutineScope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic
) : ClientInterceptor {
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: GrpcChannel
    ): ClientCall<ReqT, RespT> {
        val tracerFactory = ClientMetricsTracer.Factory(method, metrics, coroutineScope, timeSource)
        return next.newCall(method, callOptions.withStreamTracerFactory(tracerFactory))
    }
}
