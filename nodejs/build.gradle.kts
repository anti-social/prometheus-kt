import org.jetbrains.kotlin.gradle.targets.js.npm.NpmResolveTask

plugins {
    kotlin("js")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-js"))
    implementation(project(":"))
    
    testImplementation(kotlin("test-js"))
    // testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:${Versions.kotnlinxCoroutines}")
}

kotlin {
    target {
        nodejs()
        // useCommonJs()
    }
}

NpmResolveTask