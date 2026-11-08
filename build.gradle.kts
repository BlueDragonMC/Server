import java.text.SimpleDateFormat
import java.util.*

plugins {
    id("server.common-conventions")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.blossom)
    `maven-publish`
}

group = "com.bluedragonmc"
version = getPublishingVersion()

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots/") {
        content {
            includeModule("net.minestom", "minestom")
            includeModule("net.minestom", "testing")
        }
    }
    maven(url = "https://reposilite.bluedragonmc.com/releases")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.junit.api)
    testImplementation(libs.minestom.testing)
    testRuntimeOnly(libs.junit.engine)

    implementation(libs.minestom) // Minestom
    implementation(libs.atlas.projectiles)
    implementation(libs.jukebox) // Jukebox (for note block song file parsing)
    implementation(libs.minimessage) // MiniMessage
    implementation(libs.mongodb.kotlin.coroutine) // Database support
    implementation(libs.mongodb.kotlin.extensions)
    implementation(libs.bson.kotlinx)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(kotlin("reflect"))
    implementation(libs.caffeine) // Caching library for database responses
    implementation(libs.bundles.configurate) // Configurate for game configuration
    implementation(libs.bundles.messaging) // Messaging
    implementation(libs.okhttp)
    implementation(libs.polar)
    implementation(libs.slf4j)
    implementation(libs.bundles.tinylog)

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

tasks.test {
    useJUnitPlatform()
    // Matches Minestom's own test setup. Required for tests that create players directly.
    systemProperty("minestom.inside-test", "true")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.bluedragonmc.server.ServerKt"
    }
}

// Create a folder of dependency JARs to put on the classpath at runtime
val installThinDistLibs = tasks.register<Sync>("installThinDistLibs") {
    dependsOn(configurations.named("runtimeClasspath"))
    into(layout.buildDirectory.dir("thin-dist/lib"))
    from(configurations.named("runtimeClasspath")) {
        include("*.jar")
    }
}

val installThinDist = tasks.register<Copy>("installThinDist") {
    dependsOn(tasks.jar, installThinDistLibs)
    into(layout.buildDirectory.dir("thin-dist"))
    from(tasks.jar)
}
