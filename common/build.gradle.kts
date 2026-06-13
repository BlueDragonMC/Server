plugins {
    id("server.common-conventions")
    id("org.jetbrains.dokka-javadoc") version "2.2.0"
    kotlin("plugin.serialization") version "2.3.0"
    `maven-publish`
}

group = "com.bluedragonmc.server"
version = rootProject.version

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://reposilite.bluedragonmc.com/releases")
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
    implementation(libs.fastutil)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

publishing {
    val rootVersion = rootProject.version.toString()
    val inCI = rootVersion != "dev"
    repositories {
        if (inCI) {
            maven {
                name = "reposilite"
                url = uri("https://reposilite.bluedragonmc.com/releases")
                credentials(PasswordCredentials::class)
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.bluedragonmc.server"
            artifactId = "common"
            version = rootVersion

            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
        }
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
