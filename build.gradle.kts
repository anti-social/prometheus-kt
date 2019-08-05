import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    jacoco
    `maven-publish`
    id("org.ajoberstar.grgit") version Versions.grgit
}

val grgit: org.ajoberstar.grgit.Grgit by extra
val gitDescribe = grgit.describe(mapOf("match" to listOf("v*")))
    ?: "v0.1.0-SNAPSHOT"

allprojects {
    group = "dev.evo"
    version = gitDescribe.trimStart('v')

    repositories {
        mavenCentral()
        maven("https://kotlin.bintray.com/kotlinx")
    }
}

subprojects {
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = Versions.jvmTarget
        }
    }
}

kotlin {
    configureMultiPlatform(project)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:atomicfu-common:${Versions.atomicfu}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:${Versions.kotnlinxCoroutines}")
            }
        }

        val jvmMain by getting {
            languageSettings.useExperimentalAnnotation("kotlin.Experimental")

            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotnlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotnlinxCoroutines}")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:${Versions.kotnlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:atomicfu-js:${Versions.atomicfu}")
            }
        }

        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:${Versions.kotnlinxCoroutines}")
            }
        }
    }

    configurations {
        create("jvmTestOutput")
    }

    val jvmTestJar = tasks.register<Jar>("jvmTestJar") {
        from(jvm().compilations["test"].output)
        archiveClassifier.set("test")
    }
    artifacts {
        add("jvmTestOutput", jvmTestJar)
    }
}

tasks {
    val coverage = register<JacocoReport>("jacocoJVMTestReport") {
        group = "Reporting"
        description = "Generate Jacoco coverage report."
        classDirectories.setFrom(files(
            "$buildDir/classes/kotlin/jvm/main"
        ))
        sourceDirectories.setFrom(files(
            "src/commonMain/kotlin",
            "src/jvmMain/kotlin"
        ))
        executionData.setFrom(files("$buildDir/jacoco/jvmTest.exec"))
        reports {
            html.isEnabled = true
            xml.isEnabled = true
            csv.isEnabled = false
        }
    }
    named("jvmTest") {
        outputs.upToDateWhen { false }

        dependsOn(
            ":prometheus-kt-hotspot:test",
            ":prometheus-kt-ktor:test",
            ":prometheus-kt-push:jvmTest"
        )
        finalizedBy(coverage)
    }
    named("jsNodeTest") {
        outputs.upToDateWhen { false }
    }
    register("test") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn("jvmTest", "jsTest")
    }
}

publishing {
    repositories {
        bintray(project)
    }
}
