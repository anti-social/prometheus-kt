package dev.evo.prometheus.grpc

import dev.evo.prometheus.LabelSet
import dev.evo.prometheus.PrometheusMetrics
import dev.evo.prometheus.hiccup.HiccupMetrics
import dev.evo.prometheus.MetricValue

import io.grpc.ForwardingServerCall
import io.grpc.ForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerStreamTracer
import io.grpc.Status

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.handleCoroutineException
import kotlin.time.Duration.Companion.seconds
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
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ServerStreamTracer() {

    // Store matric values to not to wrap all methods with `runBlocking`.
    // So only 2 methods require `runBlocking` call:
    // - `serverCallStarted`
    // - `streamClosed`
    @Volatile
    private var msgReceivedTotal: MetricValue.CounterLong? = null
    @Volatile
    private var msgSentTotal: MetricValue.CounterLong? = null
    @Volatile
    private var bytesReceived: MetricValue.CounterLong? = null
    @Volatile
    private var bytesSent: MetricValue.CounterLong? = null
    @Volatile
    private var callInfo: ServerCallInfo<*, *>? = null
    @Volatile
    private var callStartedAt: TimeMark? = null

    open class Factory<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
        private val metrics: GrpcServerMetrics<LReq, LResp>,
        private val timeSource: TimeSource = TimeSource.Monotonic
    ) : ServerStreamTracer.Factory() {
        override fun newServerStreamTracer(
            fullMethodName: String,
            headers: Metadata
        ): ServerMetricsTracer<LReq, LResp> {
            return ServerMetricsTracer(metrics, headers, timeSource = timeSource)
        }
    }

    override fun serverCallStarted(callInfo: ServerCallInfo<*, *>) = runBlocking {
        this@ServerMetricsTracer.callInfo = callInfo
        callStartedAt = timeSource.markNow()

        val labels = metrics.requestLabelsFactory().apply {
            populate(callInfo.methodDescriptor, headers)
        }

        metrics.startedTotal.getOrCreateMetricValue(labels).inc()

        msgReceivedTotal = metrics.msgReceivedTotal.getOrCreateMetricValue(labels)
        msgSentTotal = metrics.msgSentTotal.getOrCreateMetricValue(labels)
        bytesReceived = metrics.bytesReceived.getOrCreateMetricValue(labels)
        bytesSent = metrics.bytesSent.getOrCreateMetricValue(labels)
    }

    override fun streamClosed(status: Status) = runBlocking {
        val callInfo = callInfo
        if (callInfo == null) {
            return@runBlocking
        }
        val labels = metrics.responseLabelsFactory().apply {
            populate(callInfo.methodDescriptor, headers, status)
        }

        val callStartedAt = callStartedAt
        if (callStartedAt == null) {
            return@runBlocking
        }
        val duration = callStartedAt.elapsedNow()
        val requestTimeMs = duration.inWholeMicroseconds.toDouble() / 1_000_000.0
        val handledLatencyMetric = metrics.handledLatencySeconds.getOrCreateMetricValue(labels)
        val bucketIx = handledLatencyMetric.findBucketIx(requestTimeMs)
        handledLatencyMetric.observe(bucketIx, requestTimeMs)

        metrics.handledTotal.getOrCreateMetricValue(labels).inc()
    }

    override fun inboundMessageRead(
        seqNo: Int,
        optionalWireSize: Long,
        optionalUncompressedSize: Long
    ) {
        msgReceivedTotal?.add(1)
        if (optionalWireSize > 0) {
            bytesReceived?.add(optionalWireSize)
        }
    }

    override fun outboundMessageSent(
        seqNo: Int,
        optionalWireSize: Long,
        optionalUncompressedSize: Long
    ) {
        msgSentTotal?.add(1)
        if (optionalWireSize > 0) {
            bytesSent?.add(optionalWireSize)
        }
    }
}
