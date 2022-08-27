package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.module.database.Permissions
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.minestom.server.MinecraftServer
import net.minestom.server.command.ConsoleSender
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance

class ListCommand(name: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {
    syntax {
        if (sender is ConsoleSender || Permissions.hasPermission((sender as CustomPlayer).data, "command.list.full")) {
            val firstLine = formatMessageTranslated("command.list.response",
                MinecraftServer.getConnectionManager().onlinePlayers.size)
            val all = Component.join(JoinConfiguration.newlines(),
                MinecraftServer.getInstanceManager().instances.filter { it.players.isNotEmpty() }.map { instance ->
                    Component.text(instance.uniqueId.toString(), BRAND_COLOR_PRIMARY_1) + Component.text(": ",
                        BRAND_COLOR_PRIMARY_2) + getInstancePlayerList(instance)
                })
            sender.sendMessage(firstLine + Component.newline() + all)
        } else {
            val instance = (sender as Player).instance!!
            val firstLine = formatMessageTranslated("command.list.response", instance.players.size)
            sender.sendMessage(firstLine + Component.space() + getInstancePlayerList(instance))
        }
    }
}) {
    companion object {
        internal fun getInstancePlayerList(instance: Instance) = buildMessage {
            instance.players.forEachIndexed { index, player ->
                component(player.name)
                if (index < instance.players.size - 1) message(", ")
            }
        }
    }
}