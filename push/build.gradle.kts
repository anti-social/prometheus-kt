plugins {
    kotlin("multiplatform")
    jacoco
    `maven-publish`
}

kotlin {
    configureMultiPlatform(project)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
                implementation(Libs.ktor("client-core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":test-util"))
                implementation(Libs.ktor("client-mock"))
                implementation(Libs.kotlinxCoroutines("test"))

            }
        }

        val jvmMain by getting {}
        val jvmTest by getting {}

        // Execution failed for task ':kotlinNpmResolve'.
        // Cannot add a configuration with name 'prometheus-kt-prometheus-kt-push-npm' as a configuration with that name already exists.
        // https://youtrack.jetbrains.com/issue/KT-31917 - fixed in 1.3.50
        //
        // JS tests fail with:
        // CoroutinesInternalError: Fatal exception in coroutines machinery for DispatchedContinuation[NodeDispatcher@1, [object Object]].
        // Please read KDoc to 'handleFatalException' method and report this incident to maintainers
        //
        val jsMain by getting {
            dependencies {
                implementation(Libs.ktor("client-js"))
            }
        }
        val jsTest by getting {}

        val nativeMain by getting {}
        val nativeTest by getting {}
    }
}

tasks {
    // val coverage = register<JacocoReport>("jacocoJVMTestReport") {
    //     group = "Reporting"
    //     description = "Generate Jacoco coverage report."
    //     classDirectories.setFrom(
    //         files(
    //             "$buildDir/classes/kotlin/jvm/main"
    //         )
    //     )
    //     sourceDirectories.setFrom(
    //         files(
    //             "src/commonMain/kotlin"
    //         )
    //     )
    //     executionData.setFrom(files("$buildDir/jacoco/jvmTest.exec"))
    //     reports {
    //         html.isEnabled = true
    //         xml.isEnabled = true
    //         csv.isEnabled = false
    //     }
    // }
    // named("jvmTest") {
    //     outputs.upToDateWhen { false }
    //     finalizedBy("jacocoJvmTestReport")
    // }
}

configureMultiplatformPublishing(
    "prometheus-kt-push",
    "Prometheus Kotlin Client - Push Gateway Implementation"
)
