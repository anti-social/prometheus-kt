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

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":"))
    api(project(":prometheus-kt-jvm"))
    implementation("io.ktor", "ktor-server-core", Versions.ktor)

    testImplementation("io.ktor", "ktor-server-test-host", Versions.ktor)
    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", Versions.kotnlinxCoroutines)
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf(
                "-Xuse-experimental=kotlin.Experimental"
            )
        }
    }

    jacocoTestReport {
        additionalClassDirs.setFrom(
            files("${rootProject.buildDir}/classes/kotlin/jvm/main")
        )
        additionalSourceDirs.setFrom(
            files(rootProject.relativeProjectPath("../src/commonMain/kotlin"))
        )

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
