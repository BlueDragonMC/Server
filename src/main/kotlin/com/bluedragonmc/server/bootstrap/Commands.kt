package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.command.*
import com.bluedragonmc.server.command.punishment.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object Commands : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        listOf(
            JoinCommand("join", "/join <game>"),
            InstanceCommand("instance", "/instance <list|add|remove> ...", "in"),
            GameCommand("game", "/game <start|end>"),
            LobbyCommand("lobby", "/lobby", "l", "hub"),
            MessageCommand("msg", "message", "w", "tell"),
            TeleportCommand("tp", "/tp <player|<x> <y> <z>> [player|<x> <y> <z>]"),
            FlyCommand("fly"),
            GameModeCommand("gamemode", "/gamemode <survival|creative|adventure|spectator> [player]", "gm", "gmc", "gms", "gma", "gmsp"),
            KillCommand("kill", "/kill [player]"),
            SetBlockCommand("setblock", "/setblock <x> <y> <z> <block>"),
            PartyCommand("party", "/party <invite|kick|promote|warp|chat|list> ...", "p"),
            GiveCommand("give", "/give [player] <item>"),
            PunishCommand("ban", "/<ban|mute> <player> <duration> <reason>", "mute"),
            KickCommand("kick", "/kick <player> <reason>"),
            PardonCommand("pardon", "/pardon <player|ban ID>", "unban", "unmute"),
            ViewPunishmentsCommand("punishments", "/punishments <player>", "vps", "history"),
            ViewPunishmentCommand("punishment", "/punishment <id>", "vp"),
            PermissionCommand("permission", "/permission ...", "lp", "perm"),
            PingCommand("ping", "/ping", "latency"),
            MindecraftesCommand("mindecraftes", "/mindecraftes"),
            StopCommand("stop", "/stop")
        ).forEach(MinecraftServer.getCommandManager()::register)

        MinecraftServer.getCommandManager().setUnknownCommandCallback { sender, command ->
            sender.sendMessage(Component.translatable("commands.help.failed", NamedTextColor.RED))
        }
    }
}