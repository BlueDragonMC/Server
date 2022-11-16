package com.bluedragonmc.server.command

import com.bluedragonmc.server.api.Environment
import java.text.DateFormat
import java.time.Duration

class VersionCommand(name: String, usageString: String, vararg aliases: String?) :
    BlueDragonCommand(name, aliases, block = {
        usage(usageString)

        syntax {
            val commitDate = Environment.versionInfo.commitDate?.let {
                DateFormat.getDateInstance().format(it)
            } ?: "unknown commit date"
            val duration = Duration.ofMillis(System.currentTimeMillis() - startTime)
            sender.sendMessage(
                formatMessageTranslated(
                    "command.version.output",
                    "BlueDragonMC/Server",
                    Environment.versionInfo.BRANCH ?: "unknown branch",
                    Environment.versionInfo.COMMIT ?: "unknown commit",
                    commitDate,
                    duration.toHoursPart(),
                    duration.toMinutesPart(),
                    duration.toSecondsPart()
                )
            )
        }
    }) {
        companion object {
            val startTime = System.currentTimeMillis()
        }
    }