package com.bluedragonmc.server

/**
 * This file has placeholders which are automatically filled
 * by the Blossom Gradle plugin. See the root project's
 * build.gradle.kts for more context.
 */
object GitVersionInfo : VersionInfo {

    override val COMMIT: String? = "%%GIT_COMMIT%%"
    override val BRANCH: String? = "%%GIT_BRANCH%%"
    override val COMMIT_DATE: String? = "%%GIT_COMMIT_DATE%%"

}