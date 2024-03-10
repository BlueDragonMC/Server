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
            FlyCommand("fly"),
            GameCommand("game", "/game <start|end>"),
            GameModeCommand("gamemode", "/gamemode <survival|creative|adventure|spectator> [player]", "gm"),
            GameModeCommand.GameModeAdventureCommand(),
            GameModeCommand.GameModeCreativeCommand(),
            GameModeCommand.GameModeSpectatorCommand(),
            GameModeCommand.GameModeSurvivalCommand(),
            GiveCommand("give", "/give [player] <item>"),
            InstanceCommand("instance", "/instance <list|add|remove> ...", "in"),
            JoinCommand("join", "/join <game> [mode] [mapName]"),
            KickCommand("kick", "/kick <player> <reason>"),
            KillCommand("kill", "/kill [player]"),
            LeaderboardCommand("leaderboard", "/leaderboard <statistic>"),
            ListCommand("list"),
            LobbyCommand("lobby", "/lobby", "l", "hub"),
            MessageCommand("msg", "message", "w", "tell"),
            MindecraftesCommand("mindecraftes", "/mindecraftes"),
            PardonCommand("pardon", "/pardon <player|ban ID>", "unban", "unmute"),
            PartyCommand("party", "/party <invite|kick|promote|warp|chat|list> ...", "p"),
            PartyChatShorthandCommand("pchat", "/pc <message>", "pc", "partychat"),
            PingCommand("ping", "/ping", "latency"),
            PlaysoundCommand("playsound", "/playsound <sound> <source> <target> [position] [volume] [pitch]", "ps"),
            PunishCommand("ban", "/<ban|mute> <player> <duration> <reason>", "mute"),
            SetBlockCommand("setblock", "/setblock <x> <y> <z> <block>"),
            StopCommand("stop", "/stop"),
            TeleportCommand("tp", "/tp <player|<x> <y> <z>> [player|<x> <y> <z>]"),
            TimeCommand("time", "/time <add|query|rate|set> ..."),
            VersionCommand("version", "/version", "icanhasminestom", "ver", "pl"),
            ViewPunishmentCommand("punishment", "/punishment <id>", "vp"),
            ViewPunishmentsCommand("punishments", "/punishments <player>", "vps", "history"),
        ).forEach(MinecraftServer.getCommandManager()::register)

        MinecraftServer.getCommandManager().setUnknownCommandCallback { sender, command ->
            sender.sendMessage(Component.translatable("commands.help.failed", NamedTextColor.RED))
        }
    }
}