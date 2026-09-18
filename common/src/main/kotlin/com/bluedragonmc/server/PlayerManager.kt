package com.bluedragonmc.server

import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.event.PlayerJoinGameEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.utils.async.AsyncUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the roster of players in a single [Game] and handles adding,
 * removing, and teleporting them.
 */
class PlayerManager(private val game: Game) {

    private val _players: MutableList<Player> = CopyOnWriteArrayList()
    val players: List<Player> = _players

    fun add(player: Player, sendPlayer: Boolean = true): CompletableFuture<Instance> {
        GameRegistry.findGame(player)?.roster?.remove(player)
        _players.add(player)
        if (sendPlayer && (player.instance == null || !game.ownsInstance(player.instance!!))) {
            try {
                return sendToInstance(player).whenComplete { _, _ ->
                    game.callEvent(PlayerJoinGameEvent(player, game))
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                player.sendMessage(
                    Component.translatable(
                        "queue.error_sending", NamedTextColor.RED,
                        Component.translatable("queue.error.internal_server_error", NamedTextColor.DARK_GRAY)
                    )
                )
                Environment.queue.queue(player, gameType {
                    name = Environment.defaultGameName
                })
                return AsyncUtils.empty()
            }
        }
        game.callEvent(PlayerJoinGameEvent(player, game))
        return AsyncUtils.empty()
    }

    fun remove(player: Player) {
        _players.remove(player)
        game.callEvent(PlayerLeaveGameEvent(game, player))
    }

    fun removeDisconnected() {
        _players.removeIf { player -> !player.isOnline }
    }

    fun clear() {
        while (players.isNotEmpty()) remove(players.first())
    }

    fun sendToInstance(player: Player): CompletableFuture<Instance> {
        val instance = game.getModule<InstanceModule>().getSpawningInstance(player)
        if (game.hasModule<SpawnpointModule>()) {
            val spawnpoint = game.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player)
            return player.setInstance(instance, spawnpoint).thenApply { instance }
        }
        return player.setInstance(instance).thenApply { instance }
    }
}
