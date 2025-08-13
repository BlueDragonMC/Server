plugins {
    id("server.common-conventions")
    id("com.gradleup.shadow") version "9.0.1"
    kotlin("plugin.serialization") version "2.1.10"
    id("net.kyori.blossom") version "2.1.0"
    `maven-publish`
}

group = "com.bluedragonmc"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
    maven("https://reposilite.atlasengine.ca/public")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)

    implementation(libs.minestom) // Minestom
    implementation(libs.jukebox) // Jukebox (for note block song file parsing)
    implementation(libs.minimessage) // MiniMessage
    implementation(libs.kmongo) // Database support
    implementation(libs.caffeine) // Caching library for database responses
    implementation(libs.bundles.configurate) // Configurate for game configuration
    implementation(libs.bundles.messaging) // Messaging
    implementation(libs.okhttp)

    implementation(project(":common"))
}

sourceSets {
    main {
        blossom {
            val gitCommit = getOutputOf("git rev-parse --verify --short HEAD")
            val gitBranch = getOutputOf("git rev-parse --abbrev-ref HEAD")
            val gitCommitDate = getOutputOf("git log -1 --format=%ct")

            kotlinSources {
                property("gitCommit", gitCommit.orEmpty())
                property("gitBranch", gitBranch.orEmpty())
                property("gitCommitDate", gitCommitDate.orEmpty())
            }
        }
    }
}

fun getOutputOf(command: String): String? {
    try {
        val output = providers.exec {
            commandLine = command.split(" ")
        }
        return output.standardOutput.asText.get().trim()
    } catch (_: Throwable) {
        return null
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.bluedragonmc"
            artifactId = "Server"
            version = "1.0"

            from(components["java"])
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    mergeServiceFiles()
}

tasks.jar {
    dependsOn(tasks.shadowJar)
    manifest {
        attributes["Main-Class"] = "com.bluedragonmc.server.ServerKt"
    }
}