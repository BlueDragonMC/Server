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

    // Update history:
    // Originally: a04012d9bf
    // July 9th, 2022: e713cf62a7
    // July 23rd, 2022: d596992c0e
    // August 19, 2022: f5f323fef9
    implementation("com.github.Minestom:Minestom:f5f323fef9")

    // MiniMessage
    implementation("net.kyori:adventure-text-minimessage:4.11.0")

    // Database support
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:4.6.1")

    // Messaging
    implementation("com.github.bluedragonmc:messagingsystem:3abc4b8a49")
    implementation("com.github.bluedragonmc:messages:23a6e3bfc8")

    // Agones SDK integration
    implementation("com.github.Cubxity:AgonesKt:0.1.2")
    implementation("io.grpc:grpc-protobuf:1.48.0")
    runtimeOnly("io.grpc", "grpc-netty", "1.37.0")
    runtimeOnly("io.grpc", "grpc-kotlin-stub", "1.3.0")
    runtimeOnly("com.google.protobuf:protobuf-kotlin:3.21.3")

    // Configurate for game configuration
    implementation("org.spongepowered:configurate-yaml:4.1.2")
    implementation("org.spongepowered:configurate-extra-kotlin:4.1.2")

    // Caching library for database responses
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.1")
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
        attributes["Main-Class"] = "com.bluedragonmc.server.ServerKt"
    }
}