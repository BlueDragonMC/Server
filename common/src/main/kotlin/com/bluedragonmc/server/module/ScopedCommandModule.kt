package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerJoinGameEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.lang.ref.WeakReference

/**
 * This module allows a command to be registered and managed within the lifecycle of its parent [Game].
 *
 * Note that Minestom requires command names to be **globally unique**. Registering two commands with the same names
 * will use the implementation of the first one to be registered.
 */
class ScopedCommandModule : GameModule() {
    private lateinit var parent: Game

    override fun initialize(
        parent: Game,
        eventNode: EventNode<Event>
    ) {
        this.parent = parent
        eventNode.addListener(PlayerJoinGameEvent::class.java) { event ->
            event.player.refreshCommands()
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            event.player.refreshCommands()
        }
    }

    override fun deinitialize() {
        registeredCommands.forEach { (_, cmd) ->
            cmd.games.removeAll { it.get() == parent }
        }

        unregisterUnusedCommands()

        parent.players.forEach(Player::refreshCommands)
    }

    /**
     * Removes commands from the Minestom command manager that aren't currently registered by any games
     */
    private fun unregisterUnusedCommands() {
        registeredCommands.entries.removeAll { (_, cmd) ->
            val isUnused = cmd.games.all { it.get() == null }
            if (isUnused) {
                MinecraftServer.getCommandManager().unregister(cmd.command)
            }
            isUnused
        }
    }

    fun registerCommand(commandGenerator: () -> Command) {
        val command = commandGenerator()
        val originalCondition = command.condition

        var registration = registeredCommands[command.name]
        if (registration == null && MinecraftServer.getCommandManager().getCommand(command.name) != null) {
            error("Command was registered outside of ScopedCommandModule")
        }

        if (registration == null) {
            val newValue = ScopedCommand(command, mutableListOf())
            registeredCommands[command.name] = newValue
            registration = newValue
            command.condition = condition@{ commandSender, name ->
                val player = commandSender as? Player ?: return@condition false
                if (originalCondition?.canUse(player, name) == false) {
                    return@condition false
                }
                val currentGame = Game.findGame(player) ?: return@condition false
                if (!currentGame.players.contains(player)) return@condition false // This happens when currentGame owns the instance that the player is in, but they haven't been added to the player list. In this case, they shouldn't see the command.
                return@condition registration.games.any { it.get() == currentGame }
            }
            MinecraftServer.getCommandManager().register(command)

            parent.players.forEach(Player::refreshCommands)
        }

        if (registration.games.none { it.get() == parent }) {
            registration.games.add(WeakReference(parent))
        }
    }

    fun unregisterCommand(command: Command) {
        val registration = registeredCommands[command.name] ?: return

        registration.games.removeAll { it.get() == parent }

        unregisterUnusedCommands()
        parent.players.forEach(Player::refreshCommands)
    }

    companion object {
        /**
         * Minestom command registrations are global; two commands with the same name can't both be registered.
         * We work around this by remembering which command names have already been registered.
         *
         * Then, if a game attempts to register an already-registered command, we just allow that game's players
         * to see the existing command.
         *
         * This relies on the assumption that commands with the same name will always behave the same.
         */
        private val registeredCommands: MutableMap<String, ScopedCommand> = mutableMapOf()
    }

    private data class ScopedCommand(val command: Command, val games: MutableList<WeakReference<Game>>)
}
