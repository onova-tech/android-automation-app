// Pure Kotlin/JVM core: workflow language, parsers, element resolution, plugin packages.
// No Android dependencies, so it is shared by the app and by the `agp` tool in the plugins repository.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "com.proj.automation"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.yaml:snakeyaml:2.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.11")
}

tasks.test {
    useJUnitPlatform()
}
