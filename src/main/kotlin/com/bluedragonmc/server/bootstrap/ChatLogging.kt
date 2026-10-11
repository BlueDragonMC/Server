package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.service.ChatLogger
import com.bluedragonmc.server.utils.listen
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerCommandEvent

/**
 * Records players' chat messages and commands to the database.
 */
object ChatLogging : Bootstrap() {

    override fun hook(eventNode: EventNode<Event>) {
        eventNode.listen<PlayerChatEvent> { event ->
            ChatLogger.logChat(event.player as CustomPlayer, event.rawMessage, event.recipients)
        }

        eventNode.listen<PlayerCommandEvent> { event ->
            val commandText = event.command
            val command = MinecraftServer.getCommandManager().getCommand(commandText.substringBefore(' '))
            val arguments = commandText.substringAfter(' ', "")
            if (command is BlueDragonCommand && !command.shouldLogAsCommand(arguments)) return@listen
            ChatLogger.logCommand(event.player as CustomPlayer, "/$commandText")
        }
    }
}
