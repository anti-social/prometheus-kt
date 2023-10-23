package dev.evo.prometheus.grpc

import dev.evo.prometheus.LabelSet

import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status

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
