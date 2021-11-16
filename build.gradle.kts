import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    jacoco
    `maven-publish`
    signing
    id("org.ajoberstar.grgit") version Versions.grgit
    id("io.github.gradle-nexus.publish-plugin")
}

val gitDescribe = grgit.describe(mapOf("match" to listOf("v*"), "tags" to true))
    ?: "v0.0.0-SNAPSHOT"

val notPublishedProjects = setOf("test-util")

allprojects {
    group = "dev.evo.prometheus"
    version = gitDescribe.trimStart('v')

    val isProjectPublished = name !in notPublishedProjects
    if (isProjectPublished) {
        apply {
            plugin("maven-publish")
            plugin("signing")
        }

        signing {
            sign(publishing.publications)
        }
    }

    repositories {
        mavenCentral()
    }
}

allprojects {
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = Versions.jvmTarget
        }
    }
    tasks.withType<JavaCompile> {
        targetCompatibility = Versions.jvmTarget
    }
}

kotlin {
    configureMultiPlatform(project)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotnlinxCoroutines}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":test-util"))
            }
        }

        val jvmMain by getting {
            languageSettings.useExperimentalAnnotation("kotlin.Experimental")
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotnlinxCoroutines}")
            }
        }

        val jsMain by getting {}

        val nativeMain by getting {}
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
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
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
    // named("jsNodeTest") {
    //     outputs.upToDateWhen { false }
    // }
    register("test") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(
            "jvmTest"
            // "jsTest"
        )
    }
}

extra["projectUrl"] = uri("https://github.com/anti-social/prometheus-kt")
configureMultiplatformPublishing("prometheus-kt", "Prometheus Kotlin Client")

nexusPublishing {
    repositories {
        configureSonatypeRepository(project)
    }
}
