package com.bluedragonmc.games.infection.module

import com.bluedragonmc.games.infection.InfectionGame
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.gameplay.NPCModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.module.minigame.TimedRespawnModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import java.time.Duration

/**
 * Creates a Survivors team and an Infected team.
 * When Survivors die, they become Infected. Infected players look like zombies.
 * Specifically designed for [InfectionGame], but may be used for other games too.
 * Uses `additionalLocations[0][0]` in the map database for the single infected spawnpoint.
 * Requires on start: [SpawnpointModule], [TeamModule], [TimedRespawnModule], [WinModule]
 */
@DependsOn(
    SpawnpointModule::class,
    TeamModule::class,
    TimedRespawnModule::class,
    WinModule::class
)
class InfectionModule(val scoreboardBinding: SidebarModule.ScoreboardBinding? = null) : GameModule() {
    private lateinit var parent: Game
    private val survivorsTeam =
        TeamModule.Team(Component.translatable("game.infection.team.survivors", NamedTextColor.GREEN), allowFriendlyFire = false)
    private val infectedTeam =
        TeamModule.Team(Component.text("game.infection.team.infected", NamedTextColor.RED), allowFriendlyFire = true)
    private val skins = hashMapOf<Player, PlayerSkin?>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            skins[event.player] = event.player.skin
        }

        eventNode.addListener(GameStartEvent::class.java) {
            survivorsTeam.players.addAll(parent.players)
            parent.sendMessage(Component.translatable("game.infection.warning",
                BRAND_COLOR_PRIMARY_2,
                Component.text("20", BRAND_COLOR_PRIMARY_1)))
            scoreboardBinding?.update()
            MinecraftServer.getSchedulerManager().buildTask { infectRandomPlayer() }.delay(Duration.ofSeconds(20))
                .schedule()
        }

        eventNode.addListener(TimedRespawnModule.TimedRespawnEvent::class.java) { event ->
            if (survivorsTeam.players.contains(event.player)) infect(event.player)
        }

        eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
            if (
                event.target is Player &&
                survivorsTeam.players.contains(event.target) &&
                infectedTeam.players.contains(event.attacker)
            ) {
                (event.target as Player).kill()
            }
        }

        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            disinfect(event.player)
        }
    }

    override fun deinitialize() {
        infectedTeam.players.forEach { it.skin = skins[it] }
    }

    private fun infectRandomPlayer() {
        infect(parent.players.random())
    }

    private fun infect(player: Player) {
        parent.sendMessage(Component.translatable("game.infection.infected",
            BRAND_COLOR_PRIMARY_2,
            player.name))
        player.skin = NPCModule.NPCSkins.ZOMBIE.skin
        survivorsTeam.players.remove(player)
        infectedTeam.players.add(player)
        player.respawnPoint = parent.mapData!!.additionalLocations[0][0]
        player.teleport(parent.mapData!!.additionalLocations[0][0])
        if (survivorsTeam.players.size == 1) parent.getModule<WinModule>().declareWinner(survivorsTeam.players[0])
        scoreboardBinding?.update()
    }

    private fun disinfect(player: Player) {
        player.skin = skins[player]
        infectedTeam.players.remove(player)
        survivorsTeam.players.add(player)
        player.respawnPoint = parent.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player)
        scoreboardBinding?.update()
    }

    fun isInfected(player: Player): Boolean {
        return infectedTeam.players.contains(player)
    }

}