pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "kotlin2js") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
}
include("benchmarks", "hotspot", "ktor", "nodejs")

rootProject.name = "prometheus-kt"

project(":hotspot").name = "prometheus-kt-hotspot"
project(":ktor").name = "prometheus-kt-ktor"
project(":nodejs").name = "prometheus-kt-nodejs"

enableFeaturePreview("GRADLE_METADATA")
