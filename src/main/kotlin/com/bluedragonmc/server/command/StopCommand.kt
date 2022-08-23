package com.bluedragonmc.server.command

import net.minestom.server.MinecraftServer
import java.time.Duration
import kotlin.system.exitProcess

class StopCommand(name: String, usageString: String, vararg aliases: String?) :
    BlueDragonCommand(name, aliases, block = {

        val secondsArgument by IntArgument

        usage(usageString)

        syntax(secondsArgument) {
            sender.sendMessage(formatMessageTranslated("command.stop.response", get(secondsArgument)))
            MinecraftServer.getSchedulerManager().buildTask {
                MinecraftServer.stopCleanly()
                exitProcess(0)
            }.delay(Duration.ofSeconds(get(secondsArgument).toLong())).schedule()
        }

        syntax {
            MinecraftServer.stopCleanly()
            exitProcess(0)
        }
    })