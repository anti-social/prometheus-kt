include(
    "benchmarks",
    "grpc",
    "hotspot",
    "ktor",
    "push",
    "test-util",
    "samples:http-app",
    "samples:processing-app",
)

rootProject.name = "prometheus-kt"

project(":grpc").name = "prometheus-kt-grpc"
project(":hotspot").name = "prometheus-kt-hotspot"
project(":ktor").name = "prometheus-kt-ktor"
project(":push").name = "prometheus-kt-push"
