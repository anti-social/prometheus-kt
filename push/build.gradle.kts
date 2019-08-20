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
                implementation("io.ktor:ktor-client-core:${Versions.ktor}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("io.ktor:ktor-client-mock:${Versions.ktor}")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core-jvm:${Versions.ktor}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.ktor:ktor-client-mock-jvm:${Versions.ktor}")
            }
        }

        // Execution failed for task ':kotlinNpmResolve'.
        // Cannot add a configuration with name 'prometheus-kt-prometheus-kt-push-npm' as a configuration with that name already exists.
        // https://youtrack.jetbrains.com/issue/KT-31917 - fixed in 1.3.50
        // val jsMain by getting {
        //     dependencies {
        //         implementation("io.ktor:ktor-client-js:${Versions.ktor}")
        //     }
        // }
        // val jsTest by getting {
        //     dependencies {
        //         implementation("io.ktor:ktor-client-mock-js:${Versions.ktor}")
        //     }
        // }

        val nativeMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core-native:${Versions.ktor}")
            }
        }
        val nativeTest by getting {
            dependencies {
                implementation("io.ktor:ktor-client-mock-native:${Versions.ktor}")
            }
        }
    }
}

tasks {
    val coverage = register<JacocoReport>("jacocoJVMTestReport") {
        group = "Reporting"
        description = "Generate Jacoco coverage report."
        classDirectories.setFrom(
            files(
                "$buildDir/classes/kotlin/jvm/main"
            )
        )
        sourceDirectories.setFrom(
            files(
                "src/commonMain/kotlin"
            )
        )
        executionData.setFrom(files("$buildDir/jacoco/jvmTest.exec"))
        reports {
            html.isEnabled = true
            xml.isEnabled = true
            csv.isEnabled = false
        }
    }
    named("jvmTest") {
        outputs.upToDateWhen { false }
        finalizedBy(coverage)
    }
}

publishing {
    configureMultiplatformPublishing(project)
}
