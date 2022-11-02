package com.bluedragonmc.server

import java.util.*

interface VersionInfo {

    val COMMIT: String?
    val BRANCH: String?
    val COMMIT_DATE: String?

    val commitDate: Date?
        get() = COMMIT_DATE?.toLongOrNull()?.let {
            Date(it * 1_000)
        }
}