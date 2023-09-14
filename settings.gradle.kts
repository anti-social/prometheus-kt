include(
    "benchmarks",
    "hotspot",
    "ktor",
    "proc",
    "push",
    "test-util",
    "samples:http-app",
    "samples:processing-app",
)

rootProject.name = "prometheus-kt"

project(":hotspot").name = "prometheus-kt-hotspot"
project(":ktor").name = "prometheus-kt-ktor"
project(":proc").name = "prometheus-kt-proc"
project(":push").name = "prometheus-kt-push"
