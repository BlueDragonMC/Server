import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("server.common-conventions")
    id("com.github.johnrengelman.shadow") version "7.1.0"
    kotlin("plugin.serialization") version "1.6.21"
    id("net.kyori.blossom") version "1.3.1"
}

group = "com.bluedragonmc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://jitpack.io")
}

subprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        maven(url = "https://jitpack.io")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.junit.api)
    testImplementation(libs.minestom.testing)
    testRuntimeOnly(libs.junit.engine)

    implementation(libs.minestom) // Minestom
    implementation(libs.minimessage) // MiniMessage
    implementation(libs.kmongo) // Database support
    implementation(libs.caffeine) // Caching library for database responses
    implementation(libs.bundles.agones) // Agones SDK integration
    implementation(libs.bundles.configurate) // Configurate for game configuration
    implementation(libs.bundles.messaging) // Messaging

    implementation(project(":common"))
    implementation(project(":games:skyfall"))
    implementation(project(":games:arenapvp"))
    implementation(project(":games:bedwars"))
    implementation(project(":games:fastfall"))
    implementation(project(":games:infection"))
    implementation(project(":games:infinijump"))
    implementation(project(":games:lobby"))
    implementation(project(":games:pvpmaster"))
    implementation(project(":games:skywars"))
    implementation(project(":games:teamdeathmatch"))
    implementation(project(":games:wackymaze"))
}

blossom {
    val gitCommit = getOutputOf("git rev-parse --verify --short HEAD")
    val gitBranch = getOutputOf("git rev-parse --abbrev-ref HEAD")
    val gitCommitDate = getOutputOf("git log -1 --format=%ct")

    replaceToken("\"%%GIT_COMMIT%%\"", if (gitCommit == null) "null" else "\"${gitCommit}\"")
    replaceToken("\"%%GIT_BRANCH%%\"", if (gitBranch == null) "null" else "\"${gitBranch}\"")
    replaceToken("\"%%GIT_COMMIT_DATE%%\"", if (gitCommitDate == null) "null" else "\"${gitCommitDate}\"")
}

fun getOutputOf(command: String): String? {
    try {
        val stream = org.apache.commons.io.output.ByteArrayOutputStream()
        project.exec {
            commandLine = command.split(" ")
            standardOutput = stream
        }
        return String(stream.toByteArray()).trim()
    } catch (e: Throwable) {
        return null
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.jar {
    dependsOn(tasks.shadowJar)
    manifest {
        attributes["Main-Class"] = "com.bluedragonmc.server.ServerKt"
    }
}