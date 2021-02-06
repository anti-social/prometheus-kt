import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

fun KotlinMultiplatformExtension.configureMultiPlatform(project: Project, disableJs: Boolean = false) {
    configureTargets(project, disableJs = disableJs)
    configureSourceSets(project, disableJs = disableJs)
}

fun KotlinMultiplatformExtension.configureTargets(project: Project, disableJs: Boolean = false) {
    jvm {
        this.compilations
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

    if (!disableJs) {
        js {
            nodejs()

            compilations.all {
                kotlinOptions {
                    moduleKind = "umd"
                    sourceMap = true
                }
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
}

fun KotlinMultiplatformExtension.configureSourceSets(project: Project, disableJs: Boolean = false) {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        if (!disableJs) {
            val jsMain by getting {
                dependencies {
                    implementation(kotlin("stdlib-js"))
                }
            }
            val jsTest by getting {
                dependencies {
                    implementation(kotlin("test-js"))
                }
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }
        val nativeTargetNames = targets.withType<KotlinNativeTarget>().names
        project.configure(nativeTargetNames.map { getByName("${it}Main") }) {
            dependsOn(nativeMain)
        }
        project.configure(nativeTargetNames.map { getByName("${it}Test") }) {
            dependsOn(nativeTest)
        }
    }
}
