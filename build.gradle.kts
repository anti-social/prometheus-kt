import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
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

val publishedProjects = allprojects
    .filter { p -> p.name.startsWith("prometheus") }
    .toSet()
val publishedKotlinSourceDirs = publishedProjects
    .flatMap { p ->
        listOf(
            "${p.projectDir}/src/commonMain/kotlin",
            "${p.projectDir}/src/jvmMain/kotlin",
            "${p.projectDir}/src/main/kotlin",
        )
    }
val publishedKotlinClassDirs = publishedProjects
    .map { p ->
        "${p.layout.buildDirectory}/classes/kotlin/jvm/main"
    }


allprojects {
    group = "dev.evo.prometheus"
    version = gitDescribe.trimStart('v')

    val isProjectPublished = this in publishedProjects
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

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = Versions.jvmTarget
            freeCompilerArgs += listOf(
                "-opt-in=kotlin.time.ExperimentalTime",
            )
        }
    }
    tasks.withType<JavaCompile> {
        targetCompatibility = Versions.jvmTarget
    }

    afterEvaluate {
        val coverage = tasks.register<JacocoReport>("jacocoJvmTestReport") {
            group = "Reporting"
            description = "Generate Jacoco coverage report."

            classDirectories.setFrom(publishedKotlinClassDirs)
            sourceDirectories.setFrom(publishedKotlinSourceDirs)

            executionData.setFrom(files("${layout.buildDirectory}/jacoco/jvmTest.exec"))
            reports {
                html.required.set(true)
                xml.required.set(true)
                csv.required.set(false)
            }
        }

        tasks.withType<Test> {
            outputs.upToDateWhen { false }

            testLogging {
                events = mutableSetOf<TestLogEvent>().apply {
                    add(TestLogEvent.FAILED)
                    if (project.hasProperty("showPassedTests")) {
                        add(TestLogEvent.PASSED)
                    }
                }
                exceptionFormat = TestExceptionFormat.FULL
            }
        }

        tasks.findByName("jvmTest")?.apply {
            outputs.upToDateWhen { false }
            finalizedBy(coverage)
        }
        tasks.findByName("jsNodeTest")?.apply {
            outputs.upToDateWhen { false }
        }
        tasks.findByName("linuxX64Test")?.apply {
            outputs.upToDateWhen { false }
        }
        val testTask = tasks.findByName("test")?.apply {
            outputs.upToDateWhen { false }
            finalizedBy(coverage)
        }
        if (testTask != null) {
            tasks.register("allTests") {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                dependsOn(testTask)
            }
            tasks.register("jvmTest") {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                dependsOn(testTask)
            }
        }
    }
}

kotlin {
    configureMultiPlatform(project)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(Libs.atomicfu())
                implementation(Libs.kotlinxCoroutines("core"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":test-util"))
                implementation(Libs.kotlinxCoroutines("test"))
            }
        }

        val jvmMain by getting {}

        val jvmTest by getting {}

        val jsMain by getting {}

        val nativeMain by getting {}

        val nativeTest by getting {}
    }
}

tasks {
    named("jvmTest") {
        outputs.upToDateWhen { false }

        dependsOn(
            ":prometheus-kt-hotspot:test",
            ":prometheus-kt-ktor:test",
            ":prometheus-kt-push:jvmTest"
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
