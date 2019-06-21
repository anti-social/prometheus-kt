plugins {
    kotlin("multiplatform") version "1.3.31"
    jacoco
}

group = "dev.evo"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val atomicfuVersion = "0.12.8"
val kotlinxCoroutinesVersion = "1.2.1"

kotlin {
    jvm()

    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xnew-inference")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:atomicfu-common:$atomicfuVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$kotlinxCoroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            languageSettings.useExperimentalAnnotation("kotlin.Experimental")

            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
            }
        }
        jvm().compilations["test"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}

tasks {
    val coverage = register<JacocoReport>("jacocoJVMTestReport") {
        group = "Reporting"
        description = "Generate Jacoco coverage report."
        classDirectories.setFrom(
            fileTree("$buildDir/classes/kotlin/jvm/main")
        )
        val coverageSourceDirs = listOf(
            "src/commonMain/kotlin",
            "src/jvmMain/kotlin"
        )
        additionalSourceDirs.setFrom(files(coverageSourceDirs))
        sourceDirectories.setFrom(files(coverageSourceDirs))
        executionData.setFrom(files("$buildDir/jacoco/jvmTest.exec"))
        reports {
            html.isEnabled = true
            xml.isEnabled = true
            csv.isEnabled = false
        }
    }
    named("jvmTest") {
        outputs.upToDateWhen { false }

        dependsOn(":prometheus-kt-ktor:test")
        finalizedBy(coverage)
    }
}
