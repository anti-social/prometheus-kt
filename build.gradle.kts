import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
        maven(Repo.kotlinEap)
    }
    // dependencies {
    //     classpath("org.jetbrains.kotlin.multiplatform:kotlin-gradle-plugin:${Versions.kotlin}")
    // }
}

// apply {
//     plugin("kotlin-multiplatform")
// }

plugins {
    kotlin("multiplatform") version Versions.kotlin
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
        maven(Repo.kotlinEap)
        maven(Repo.kotlinx)
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

        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
    }

    js {
        nodejs()

        compilations.all {
            kotlinOptions {
                moduleKind = "umd"
                sourceMap = true
            }
        }
    }

    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")

    // Create target for the host platform.
    val hostTarget = when {
        hostOs == "Mac OS X" -> macosX64()
        hostOs == "Linux" -> {
            linuxX64()
            // Kotlinx coroutines library isn't built for Linux ARM targets
            // linuxArm32Hfp()
        }
        isMingwX64 -> mingwX64()
        else -> throw GradleException("Host OS [$hostOs] is not supported in Kotlin/Native $project.")
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
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:${Versions.kotnlinxCoroutines}")
            }
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }

        val nativeTargetNames = targets.withType<KotlinNativeTarget>().names
        configure(nativeTargetNames.map { getByName("${it}Main") }) {
            dependsOn(nativeMain)
        }
        configure(nativeTargetNames.map { getByName("${it}Test") }) {
            dependsOn(nativeTest)
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

        dependsOn(":prometheus-kt-hotspot:test", ":prometheus-kt-ktor:test")
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
