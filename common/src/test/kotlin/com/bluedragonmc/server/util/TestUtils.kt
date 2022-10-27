package com.bluedragonmc.server.util

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.instance.InstanceModule
import net.minestom.server.api.Env
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import java.time.Duration
import kotlin.test.assertTrue

object TestUtils {

    fun emptyGame(env: Env, vararg modules: GameModule): Game {
        val game = EmptyGame(env)
        game.useModules(listOf(*modules))
        return game
    }

    fun addPlayer(env: Env, game: Game): Player {
        val player = env.createPlayer(game.getInstance(), Pos.ZERO)
        addPlayer(env, game, player)
        return player
    }

    fun addPlayer(env: Env, game: Game, player: Player) {
        waitForSpawn(env, player)
        game.addPlayer(player)
    }

    fun startGame(game: Game) = game.callEvent(GameStartEvent(game))

    fun waitForSpawn(env: Env, player: Player) {
        val spawned = env.tickWhile({ player.instance == null }, Duration.ofSeconds(2))
        assertTrue(spawned, "Player spawned in the instance within 2 seconds")
    }

    private class EmptyGame(env: Env) : Game("empty", "empty", null) {

        override fun useMandatoryModules() {
            // no mandatory modules should be registered
        }

        init {
            use(FlatInstanceModule(env))
        }

        override fun loadMapData() {
            // map data should not be loaded from anywhere
        }

        override val preloadSpawnChunks = false
        override val autoRemoveInstance = false
    }

    private class FlatInstanceModule(private val env: Env) : InstanceModule() {

        private lateinit var instance: Instance

        override fun getInstance() = instance

        override fun initialize(parent: Game, eventNode: EventNode<Event>) {
            instance = env.createFlatInstance()
        }

    }
}