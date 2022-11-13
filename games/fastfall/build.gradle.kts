plugins {
    id("server.common-conventions")
}

group = "com.bluedragonmc.games"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.bundles.configurate)
    implementation(libs.minestom)
    implementation(libs.kmongo)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}