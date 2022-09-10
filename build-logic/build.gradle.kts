plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("kotlin-jvm").get())
}
