plugins {
    id("server.common-conventions")
    kotlin("plugin.serialization") version "1.6.21"
}

group = "com.bluedragonmc.server"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {

    testImplementation(kotlin("test"))
    testImplementation(libs.minestom.testing)
    testImplementation(libs.mockk)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")

    implementation(libs.minestom)
    implementation(libs.kmongo)
    implementation(libs.caffeine)
    implementation(libs.minimessage)
    implementation(libs.bundles.configurate)
    implementation(libs.bundles.messaging)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}