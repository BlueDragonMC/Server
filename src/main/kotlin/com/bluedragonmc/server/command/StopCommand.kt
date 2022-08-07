package com.bluedragonmc.server.command

import net.minestom.server.MinecraftServer
import java.time.Duration

class StopCommand(name: String, usageString: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, block = {

    val secondsArgument by IntArgument

    usage(usageString)

    syntax(secondsArgument) {
        MinecraftServer.getSchedulerManager().buildTask {
            MinecraftServer.stopCleanly()
        }.delay(Duration.ofSeconds(get(secondsArgument).toLong())).schedule()
    }

    syntax {
        MinecraftServer.stopCleanly()
    }
})