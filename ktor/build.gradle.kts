import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    kotlin("jvm")
    jacoco
}

repositories {
    jcenter()
}

val ktorVersion = "1.2.2"
val kotlinxCoroutinesVersion = "1.2.1"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":"))
    implementation("io.ktor", "ktor-server-core", ktorVersion)

    testImplementation("io.ktor", "ktor-server-test-host", ktorVersion)
    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", kotlinxCoroutinesVersion)
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            freeCompilerArgs = listOf(
                "-Xuse-experimental=kotlin.Experimental"
            )
        }
    }

    jacocoTestReport {
        reports {
            xml.isEnabled = true
            html.isEnabled = true
            csv.isEnabled = false
        }
    }

    named("test") {
        outputs.upToDateWhen { false }

        finalizedBy(jacocoTestReport)
    }

}
