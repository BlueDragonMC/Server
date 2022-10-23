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
    implementation(libs.messages)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}