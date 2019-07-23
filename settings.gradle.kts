include("benchmarks", "hotspot", "ktor")

rootProject.name = "prometheus-kt"

project(":hotspot").name = "prometheus-kt-hotspot"
project(":ktor").name = "prometheus-kt-ktor"

enableFeaturePreview("GRADLE_METADATA")
