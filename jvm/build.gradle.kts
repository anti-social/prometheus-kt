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
    implementation(project(":"))

    implementation(kotlin("test"))
    implementation(kotlin("test-junit"))
    testImplementation(project(":", configuration = "jvmTestOutput"))
}

tasks {
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