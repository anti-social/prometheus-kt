val ktorVersion = "1.2.1"
val slf4jVersion = "1.7.26"

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("dev.evo:prometheus-kt-ktor")
    compile("io.ktor:ktor-server-netty:$ktorVersion")
    compile("org.slf4j:slf4j-simple:$slf4jVersion")
}

application {
    mainClassName = "MainKt"
}
