plugins {
    kotlin("multiplatform")
    jacoco
    `maven-publish`
}

kotlin {
    configureMultiPlatform(project, disableJs = true)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
                // implementation(Libs.kotlinxIO("core"))
                implementation(Libs.okio())
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":test-util"))
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation(Libs.kotlinxCoroutines("test"))
            }
        }

        val jvmMain by getting {}
        val jvmTest by getting {}

        val nativeMain by getting {}
        val nativeTest by getting {}
    }
}

configureMultiplatformPublishing(
    "prometheus-kt-proc",
    "Prometheus Kotlin Client - Procfs Metrics"
)
