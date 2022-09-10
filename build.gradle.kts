import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    id("server.common-conventions")
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

    implementation(libs.minestom) // Minestom
    implementation(libs.minimessage) // MiniMessage
    implementation(libs.kmongo) // Database support
    implementation(libs.caffeine) // Caching library for database responses
    implementation(libs.bundles.agones) // Agones SDK integration
    implementation(libs.bundles.configurate) // Configurate for game configuration
    implementation(libs.bundles.messaging) // Messaging

    implementation(project(":common"))
    implementation(project(":games:arenapvp"))
    implementation(project(":games:bedwars"))
    implementation(project(":games:fastfall"))
    implementation(project(":games:infection"))
    implementation(project(":games:infinijump"))
    implementation(project(":games:lobby"))
    implementation(project(":games:pvpmaster"))
    implementation(project(":games:skywars"))
    implementation(project(":games:teamdeathmatch"))
    implementation(project(":games:wackymaze"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.jar {
    dependsOn(tasks.shadowJar)
    manifest {
        attributes["Main-Class"] = "com.bluedragonmc.server.ServerKt"
    }
}