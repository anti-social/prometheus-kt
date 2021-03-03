import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

application {
    mainClassName = "MainKt"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf(
        "-Xuse-experimental=kotlin.Experimental"
    )
}