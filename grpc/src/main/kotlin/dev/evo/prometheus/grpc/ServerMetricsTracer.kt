package dev.evo.prometheus.grpc

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.MetricValue

import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerStreamTracer
import io.grpc.Status

import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class GrpcServerMetrics<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
    val requestLabelsFactory: () -> LReq,
    val responseLabelsFactory: () -> LResp,
) : PrometheusMetrics() {
    val startedTotal by gaugeLong(
        "grpc_server_started_total",
        help = "Total number of requests started on the server",
        labelsFactory = requestLabelsFactory,
    )
    val handledTotal by gaugeLong(
        "grpc_server_handled_total",
        help = "Total number of requests complited by the server",
        labelsFactory = responseLabelsFactory,
    )
    val handledLatencySeconds by histogram(
        "grpc_server_handled_latency_seconds", logScale(-3, 0),
        help = "Histogram of processing requests latency (seconds)",
        labelsFactory = responseLabelsFactory,
    )

    val msgReceivedTotal by counterLong(
        "grpc_server_msg_received_total",
        help = "Total number of received stream messages",
        labelsFactory = requestLabelsFactory,
    )
    val msgSentTotal by counterLong(
        "grpc_server_msg_sent_total",
        help = "Total number of sent stream messages",
        labelsFactory = requestLabelsFactory,
    )

    val bytesReceived by counterLong(
        "grpc_server_bytes_received",
        help = "Total outbound messages size",
        labelsFactory = requestLabelsFactory
    )
    val bytesSent by counterLong(
        "grpc_server_bytes_sent",
        help = "Total inbound messages size",
        labelsFactory = requestLabelsFactory,
    )
}

fun GrpcServerMetrics(): GrpcServerMetrics<GrpcRequestLabels, GrpcResponseLabels> {
    return GrpcServerMetrics(::GrpcRequestLabels, ::GrpcResponseLabels)
}

abstract class GrpcRequestLabelSet : LabelSet() {
    abstract fun populate(methodDescriptor: MethodDescriptor<*, *>, headers: Metadata)
}

abstract class GrpcResponseLabelSet : LabelSet() {
    abstract fun populate(methodDescriptor: MethodDescriptor<*, *>, headers: Metadata, status: Status)
}

open class GrpcRequestLabels : GrpcRequestLabelSet() {
    var service by label("grpc_service")
    var method by label("grpc_method")
    var type by label("grpc_type")

    override open fun populate(methodDescriptor: MethodDescriptor<*, *>, headers: Metadata) {
        service = methodDescriptor.serviceName
        method = methodDescriptor.bareMethodName
        type = methodDescriptor.type.toString()
    }
}

open class GrpcResponseLabels : GrpcResponseLabelSet() {
    var service by label("grpc_service")
    var method by label("grpc_method")
    var type by label("grpc_type")
    var code by label("grpc_code")

    override open fun populate(
        methodDescriptor: MethodDescriptor<*, *>,
        headers: Metadata,
        status: Status
    ) {
        service = methodDescriptor.serviceName
        method = methodDescriptor.bareMethodName
        type = methodDescriptor.type.toString()
        code = status.code.toString()
    }
}

class ServerMetricsTracer<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
    private val metrics: GrpcServerMetrics<LReq, LResp>,
    private val headers: Metadata,
    private val coroutineScope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ServerStreamTracer() {

    private sealed class Event {
        class CallStarted(val callInfo: ServerCallInfo<*, *>) : Event()
        class InboundMessageRead(val wireSize: Long) : Event()
        class OutboundMessageSent(val wireSize: Long) : Event()
        class StreamClosed(val status: Status, val duration: Duration?) : Event()
        class Flush(val result: CompletableDeferred<Unit>) : Event()
    }

    private val events = Channel<Event>(4)

    @Volatile
    private lateinit var callStartMark: TimeMark

    private val job = coroutineScope.async {
        lateinit var callInfo: ServerCallInfo<*, *>
        lateinit var requestLabels: LReq

        while (true) {
            val eventResult = events.receiveCatching()
            if (eventResult.isClosed) {
                break
            }
            when (val event = eventResult.getOrThrow()) {
                is Event.CallStarted -> {
                    callInfo = event.callInfo
                    requestLabels = metrics.requestLabelsFactory().apply {
                        populate(callInfo.methodDescriptor, headers)
                    }

                    metrics.startedTotal
                        .getOrCreateMetricValue(requestLabels)
                        .inc()
                }
                is Event.StreamClosed -> {
                    val responseLabels = metrics.responseLabelsFactory().apply {
                        populate(callInfo.methodDescriptor, headers, event.status)
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

    open class Factory<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
        private val metrics: GrpcServerMetrics<LReq, LResp>,
        private val coroutineScope: CoroutineScope,
        private val timeSource: TimeSource = TimeSource.Monotonic
    ) : ServerStreamTracer.Factory() {
        override fun newServerStreamTracer(
            fullMethodName: String,
            headers: Metadata
        ): ServerMetricsTracer<LReq, LResp> {
            return ServerMetricsTracer(metrics, headers, coroutineScope, timeSource = timeSource)
        }
    }

    override fun serverCallStarted(callInfo: ServerCallInfo<*, *>) {
        callStartMark = timeSource.markNow()

        events.trySend(
            Event.CallStarted(callInfo)
        )
    }

    override fun streamClosed(status: Status) {
        events.trySend(Event.StreamClosed(status, callStartMark.elapsedNow()))
        events.close()
    }

    override fun inboundMessageRead(
        seqNo: Int,
        optionalWireSize: Long,
        optionalUncompressedSize: Long
    ) {
        events.trySend(Event.InboundMessageRead(optionalWireSize))
    }

    override fun outboundMessageSent(
        seqNo: Int,
        optionalWireSize: Long,
        optionalUncompressedSize: Long
    ) {
        events.trySend(Event.OutboundMessageSent(optionalWireSize))
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
