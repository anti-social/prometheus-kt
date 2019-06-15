import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// buildscript {
//     val atomicfu_version = "0.12.8"
//
//     dependencies {
//         classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version")
//     }
// }

plugins {
    java
    kotlin("multiplatform") version "1.3.31"
}

// apply {
//     plugin("kotlinx-atomicfu")
// }

group = "dev.evo"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val atomicfuVersion = "0.12.8"
val kotlinxCoroutinesVersion = "1.2.1"

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
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

// configure<JavaPluginConvention> {
//     sourceCompatibility = JavaVersion.VERSION_1_8
// }
// tasks.withType<KotlinCompile> {
//     kotlinOptions.jvmTarget = "1.8"
// }
