package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.game.InfectionGame
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.plus
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.EntityType
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
 * Requires on start: [DatabaseModule], [SpawnpointModule], [TeamModule], [TimedRespawnModule], [WinModule]
 */
class InfectionModule(val scoreboardBinding: SidebarModule.ScoreboardBinding? = null) : GameModule() {
    private lateinit var parent: Game
    private lateinit var mapData: MapData
    private val survivorsTeam =
        TeamModule.Team(Component.text("Survivors", NamedTextColor.GREEN), allowFriendlyFire = false)
    private val infectedTeam =
        TeamModule.Team(Component.text("Infected", NamedTextColor.RED), allowFriendlyFire = true)
    private val skins = hashMapOf<Player, PlayerSkin?>()

    override val dependencies = listOf(DatabaseModule::class, SpawnpointModule::class, TeamModule::class, TimedRespawnModule::class, WinModule::class)

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent

        DatabaseModule.IO.launch {
            mapData = parent.getModule<DatabaseModule>().getMap(parent.mapName)
        }

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            skins[event.player] = event.player.skin
        }

        eventNode.addListener(GameStartEvent::class.java) {
            survivorsTeam.players.addAll(parent.players)
            parent.sendMessage(
                Component.text(
                    "The first infected player will be chosen in ",
                    BRAND_COLOR_PRIMARY_2
                ) + Component.text("20", BRAND_COLOR_PRIMARY_1) + Component.text(" seconds!", BRAND_COLOR_PRIMARY_2)
            )
            scoreboardBinding?.update()
            MinecraftServer.getSchedulerManager().buildTask { infectRandomPlayer() }.delay(Duration.ofSeconds(20))
                .schedule()
        }

        eventNode.addListener(TimedRespawnModule.TimedRespawnEvent::class.java) { event ->
            if (survivorsTeam.players.contains(event.player)) infect(event.player)
        }

        eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
            if (event.target is Player && survivorsTeam.players.contains(event.target) && infectedTeam.players.contains(event.attacker))
                event.target.kill()
        }
    }

    override fun deinitialize() {
        while (infectedTeam.players.isNotEmpty()) disinfect(infectedTeam.players.first())
    }

    fun infectRandomPlayer() {
        infect(parent.players.random())
    }

    fun infect(player: Player) {
        parent.sendMessage(player.name + Component.text(" is now infected!", BRAND_COLOR_PRIMARY_2))
        player.skin = NPCModule.NPCSkins.ZOMBIE.skin
        survivorsTeam.players.remove(player)
        infectedTeam.players.add(player)
        player.respawnPoint = mapData.additionalLocations[0][0]
        player.teleport(mapData.additionalLocations[0][0])
        if (survivorsTeam.players.size == 1) parent.getModule<WinModule>().declareWinner(survivorsTeam.players[0])
        scoreboardBinding?.update()
    }

    fun disinfect(player: Player) {
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