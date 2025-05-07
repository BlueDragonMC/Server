plugins {
    id("server.common-conventions")
    kotlin("plugin.serialization") version "2.1.10"
    `maven-publish`
}

group = "com.bluedragonmc.server"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
    maven("https://reposilite.atlasengine.ca/public")
}

dependencies {
    implementation(libs.minestom)
    implementation(libs.atlas.projectiles)
    implementation(libs.kmongo)
    implementation(libs.caffeine)
    implementation(libs.minimessage)
    implementation(libs.bundles.configurate)
    implementation(libs.bundles.messaging)
    implementation(libs.serialization.json)
    implementation(libs.bundles.tinylog)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.bluedragonmc.server"
            artifactId = "common"
            version = "1.0"

            from(components["java"])
        }
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}