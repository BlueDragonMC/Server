import java.text.SimpleDateFormat
import java.util.*

plugins {
    id("server.common-conventions")
    id("com.gradleup.shadow") version "9.4.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("net.kyori.blossom") version "2.1.0"
    `maven-publish`
}

group = "com.bluedragonmc"
version = getPublishingVersion()

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://reposilite.bluedragonmc.com/releases")
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
    implementation(libs.polar)

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

fun isInCI() = System.getenv("CI") != null

fun getPublishingVersion(): String = if (isInCI()) {
    val commitSha = getOutputOf("git rev-parse --verify --short HEAD")
    val date = SimpleDateFormat("YYYY-MM-dd").format(Date())

    "$date-$commitSha"
} else {
    "dev"
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

tasks.jar {
    dependsOn(tasks.shadowJar)
    manifest {
        attributes["Main-Class"] = "com.bluedragonmc.server.ServerKt"
    }
}
