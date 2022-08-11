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
    implementation("com.github.Minestom:Minestom:d596992c0e")

    // MiniMessage
    implementation("net.kyori:adventure-text-minimessage:4.11.0")

    // Database support
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:4.6.1")

    // Messaging
    implementation("com.github.bluedragonmc:messagingsystem:3abc4b8a49")
    implementation("com.github.bluedragonmc:messages:a2a08c9d8e")

    // UnifiedMetrics (available at runtime as an extension)
    compileOnly("dev.cubxity.plugins", "unifiedmetrics-api", "0.3.6")

    // Agones SDK integration
    implementation("com.github.Cubxity:AgonesKt:0.1.2")
    implementation("io.grpc:grpc-protobuf:1.48.0")
    runtimeOnly("io.grpc", "grpc-netty", "1.37.0")
    runtimeOnly("io.grpc", "grpc-kotlin-stub", "1.3.0")
    runtimeOnly("com.google.protobuf:protobuf-kotlin:3.21.3")
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