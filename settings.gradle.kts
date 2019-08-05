include("benchmarks", "hotspot", "ktor", "push")

rootProject.name = "prometheus-kt"

project(":hotspot").name = "prometheus-kt-hotspot"
project(":ktor").name = "prometheus-kt-ktor"
project(":push").name = "prometheus-kt-push"

enableFeaturePreview("GRADLE_METADATA")
