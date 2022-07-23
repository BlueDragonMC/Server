package com.bluedragonmc.server.command

import com.bluedragonmc.messages.RequestUpdateMessage
import com.bluedragonmc.server.module.messaging.MessagingModule
import net.minestom.server.entity.Player
import java.util.*

class UpdateCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {
    usage(usageString)

    val repoArgument by StringArgument
    val branchArgument by StringArgument

    syntax(repoArgument, branchArgument) {
        sender.sendMessage(formatMessage("Requesting update of repo {}:{}", get(repoArgument), get(branchArgument)))
        MessagingModule.publish(RequestUpdateMessage(
            (sender as? Player)?.uuid ?: UUID(0L, 0L),
            get(repoArgument),
            get(branchArgument)
        ))
    }
})