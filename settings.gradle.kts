include("benchmarks", "hotspot", "ktor", "push", "test-util")

rootProject.name = "prometheus-kt"

project(":hotspot").name = "prometheus-kt-hotspot"
project(":ktor").name = "prometheus-kt-ktor"
project(":push").name = "prometheus-kt-push"
