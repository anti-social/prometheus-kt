package dev.evo.prometheus.grpc

import dev.evo.prometheus.PrometheusMetrics

abstract class GrpcMetrics<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
    private val namespace: String,
    private val requestLabelsFactory: () -> LReq,
    private val responseLabelsFactory: () -> LResp,
) : PrometheusMetrics() {
    val startedTotal by gaugeLong(
        "${namespace}_started_total",
        help = "Total number of requests started",
        labelsFactory = requestLabelsFactory,
    )
    val handledTotal by gaugeLong(
        "${namespace}_handled_total",
        help = "Total number of requests complited",
        labelsFactory = responseLabelsFactory,
    )

    val msgSentTotal by counterLong(
        "${namespace}_msg_sent_total",
        help = "Total number of sent stream messages",
        labelsFactory = requestLabelsFactory,
    )
    val msgReceivedTotal by counterLong(
        "${namespace}_msg_received_total",
        help = "Total number of received stream messages",
        labelsFactory = requestLabelsFactory,
    )

    val bytesSent by counterLong(
        "${namespace}_bytes_sent",
        help = "Total inbound messages size",
        labelsFactory = requestLabelsFactory,
    )
    val bytesReceived by counterLong(
        "${namespace}_bytes_received",
        help = "Total outbound messages size",
        labelsFactory = requestLabelsFactory
    )

    val handledLatencySeconds by histogram(
        "${namespace}_handled_latency_seconds", logScale(-3, 0),
        help = "Histogram of processing requests latency (seconds)",
        labelsFactory = responseLabelsFactory,
    )
}

class GrpcServerMetrics<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
    val requestLabelsFactory: () -> LReq,
    val responseLabelsFactory: () -> LResp,
) : GrpcMetrics<LReq, LResp>("grpc_server", requestLabelsFactory, responseLabelsFactory)

fun GrpcServerMetrics(): GrpcServerMetrics<GrpcRequestLabels, GrpcResponseLabels> {
    return GrpcServerMetrics(::GrpcRequestLabels, ::GrpcResponseLabels)
}

class GrpcClientMetrics<LReq: GrpcRequestLabelSet, LResp: GrpcResponseLabelSet>(
    val requestLabelsFactory: () -> LReq,
    val responseLabelsFactory: () -> LResp,
) : GrpcMetrics<LReq, LResp>("grpc_client", requestLabelsFactory, responseLabelsFactory)

fun GrpcClientMetrics(): GrpcClientMetrics<GrpcRequestLabels, GrpcResponseLabels> {
    return GrpcClientMetrics(::GrpcRequestLabels, ::GrpcResponseLabels)
}
