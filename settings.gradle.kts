include("benchmarks", "jvm", "ktor")

rootProject.name = "prometheus-kt"

project(":jvm").name = "prometheus-kt-jvm"
project(":ktor").name = "prometheus-kt-ktor"

enableFeaturePreview("GRADLE_METADATA")
