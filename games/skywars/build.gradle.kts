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
    testImplementation(project(":testing"))
    implementation(project(":common"))
    implementation(libs.minestom)
    implementation(libs.bundles.configurate)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}