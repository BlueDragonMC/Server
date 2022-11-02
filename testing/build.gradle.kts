plugins {
    id("server.common-conventions")
}

group = "com.bluedragonmc.testing"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    implementation(project(":common"))

    implementation(libs.mockk)
    implementation(libs.minestom)
    implementation(libs.minestom.testing)

    implementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}