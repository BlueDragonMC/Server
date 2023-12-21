package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.utils.clickEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.Git
import java.text.DateFormat
import java.time.Duration

class VersionCommand(name: String, usageString: String, vararg aliases: String?) :
    BlueDragonCommand(name, aliases, block = {
        usage(usageString)

        syntax {
            val commitDate = Environment.versionInfo.commitDate?.let {
                DateFormat.getDateInstance().format(it)
            } ?: "(unknown commit date)"
            val duration = Duration.ofMillis(System.currentTimeMillis() - startTime)
            sender.sendMessage(
                formatMessageTranslated(
                    "command.version.output",
                    Component.text("BlueDragonMC/Server", BRAND_COLOR_PRIMARY_1)
                        .clickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/BlueDragonMC/Server"),
                    // Branch
                    if (Environment.versionInfo.BRANCH != null) {
                        Component.text(Environment.versionInfo.BRANCH!!, BRAND_COLOR_PRIMARY_1)
                            .clickEvent(
                                ClickEvent.Action.OPEN_URL,
                                "https://github.com/BlueDragonMC/Server/tree/" + Environment.versionInfo.BRANCH!!
                            )
                    } else "(unknown branch)",
                    // Commit
                    if (Environment.versionInfo.COMMIT != null) {
                        Component.text(Environment.versionInfo.COMMIT!!, BRAND_COLOR_PRIMARY_1)
                            .clickEvent(
                                ClickEvent.Action.OPEN_URL,
                                "https://github.com/BlueDragonMC/Server/commit/" + Environment.versionInfo.COMMIT!!
                            )
                    } else "(unknown commit)",
                    commitDate,
                    // Uptime
                    duration.toHoursPart(),
                    duration.toMinutesPart(),
                    duration.toSecondsPart(),
                    Git.group() ?: "?",
                    Git.artifact() ?: "?",
                    Git.commit() ?: "?",
                    Git.branch() ?: "?"
                )
            )
            if (Environment.isDev) {
                sender.sendMessage(Component.translatable("command.version.development_warning", NamedTextColor.RED))
            }
        }
    }) {
    companion object {
        val startTime = System.currentTimeMillis()
    }
}