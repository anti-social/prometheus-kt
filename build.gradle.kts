import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") version Versions.kotlin
    jacoco
}

group = "dev.evo"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

subprojects {
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = Versions.jvmTarget
        }
    }
}

kotlin {
    jvm {
        compilations {
            val main by this
            val test by this
            listOf(main, test).forEach {
                it.kotlinOptions {
                    jvmTarget = Versions.jvmTarget
                }
            }
        }
    }

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
                implementation("org.jetbrains.kotlinx:atomicfu-common:${Versions.atomicfu}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:${Versions.kotnlinxCoroutines}")
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotnlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")
            }
        }
        jvm().compilations["test"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotnlinxCoroutines}")
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

        dependsOn(":prometheus-kt-jvm:test", ":prometheus-kt-ktor:test")
        finalizedBy(coverage)
    }
    register("test") {
        dependsOn("jvmTest")
    }
}
