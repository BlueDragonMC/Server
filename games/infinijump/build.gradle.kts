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
    implementation(libs.minestom)
    implementation(libs.bundles.messaging)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}