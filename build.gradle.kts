import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "com.bluedragonmc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))

    // Minestom
    implementation("com.github.Minestom:Minestom:a04012d9bf")

    // MiniMessage
    implementation("net.kyori:adventure-text-minimessage:4.11.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.jar {
    dependsOn(tasks.shadowJar)
    manifest {
        attributes(
            mapOf(
                "Main-Class" to "com.bluedragonmc.server.ServerKt"
            )
        )
    }
}