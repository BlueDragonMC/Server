plugins {
    id("server.common-conventions")
    id("com.github.johnrengelman.shadow") version "7.1.0"
    kotlin("plugin.serialization") version "1.6.21"
    id("net.kyori.blossom") version "2.1.0"
    `maven-publish`
}

group = "com.bluedragonmc"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
}

subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)

    implementation(libs.minestom) // Minestom
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
                property("git.commit", gitCommit)
                property("git.branch", gitBranch)
                property("git.commitDate", gitCommitDate)
            }
        }
    }
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