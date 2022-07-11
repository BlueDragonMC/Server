import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    kotlin("plugin.serialization") version "1.6.21"
}

group = "com.bluedragonmc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))

    // Minestom
    implementation("com.github.Minestom:Minestom:e713cf62a7") // Old commit: a04012d9bf

    // MiniMessage
    implementation("net.kyori:adventure-text-minimessage:4.11.0")

    // Database support
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:4.6.1")

    // Messaging
    implementation("com.github.bluedragonmc:messagingsystem:2836a1f6f3")
    implementation("com.github.bluedragonmc:messages:5d3dd0d240")
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