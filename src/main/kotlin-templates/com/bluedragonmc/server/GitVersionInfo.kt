package com.bluedragonmc.server

/**
 * This file has placeholders which are automatically filled
 * by the Blossom Gradle plugin. See the root project's
 * build.gradle.kts for more context.
 */
object GitVersionInfo : VersionInfo {

    override val COMMIT: String? = "{{ git.commit | default("Unknown") }}"
    override val BRANCH: String? = "{{ git.branch | default("Unknown") }}"
    override val COMMIT_DATE: String? = "{{ git.commitDate | default("Unknown") }}"

}