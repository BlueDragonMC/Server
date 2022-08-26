package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.PlayerKillPlayerEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.minigame.KitsModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.miniMessage
import net.kyori.adventure.title.Title
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * A module that gives an armor level to each player.
 * Whenever they get a kill ([PlayerKillPlayerEvent] is called), the killer goes down an armor level.
 * When a player gets a kill with the lowest armor level (leather), they win the game.
 * This module was designed for PvPMaster, but it can be used for other games.
 */
class ArmorLevelsModule(private val levels: List<KitsModule.Kit>) : GameModule() {

    override val dependencies = listOf(WinModule::class) // TODO scoreboard bindings

    private val armorLevels = hashMapOf<Player, Int>()
    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(GameStartEvent::class.java) { event ->
            for (player in parent.players) {
                player.setArmorLevel(levels.size - 1)
            }
        }

        eventNode.addListener(PlayerKillPlayerEvent::class.java) { event ->
            event.attacker.decrementArmorLevel()
            event.attacker.health = event.attacker.health + 10
        }
    }

    /**
     * Sets the player's armor level, notifies them, and gives them all the right items.
     */
    fun Player.setArmorLevel(newLevel: Int) {
        if (newLevel <= 0) {
            parent.getModule<WinModule>().declareWinner(this)
            inventory.clear()
            return
        }
        armorLevels[this] = newLevel

        val armorLevel = levels[newLevel]

        showTitle(Title.title(armorLevel.name, miniMessage.deserialize(armorLevel.description)))

        inventory.clear()
        for (item in armorLevel.items) {
            inventory.setItemStack(item.key, item.value)
        }
    }

    private fun Player.decrementArmorLevel() {
        setArmorLevel(armorLevels.getOrDefault(this, levels.size - 1) - 1)
    }
}