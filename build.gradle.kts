import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") version Versions.kotlin
    jacoco
}

group = "dev.evo"
version = "0.1-SNAPSHOT"

allprojects {
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

    js {
        nodejs()
    }

    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")

    // Create target for the host platform.
    val hostTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS '$hostOs' is not supported in Kotlin/Native $project.")
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
        val jvmMain by getting {
            languageSettings.useExperimentalAnnotation("kotlin.Experimental")

            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotnlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotnlinxCoroutines}")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:${Versions.kotnlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:atomicfu-js:${Versions.atomicfu}")
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
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

        dependsOn(":prometheus-kt-jvm:test", ":prometheus-kt-ktor:test")
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
