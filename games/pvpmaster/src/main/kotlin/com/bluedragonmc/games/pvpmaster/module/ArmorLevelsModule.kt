package com.bluedragonmc.games.pvpmaster.module

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.PlayerKillPlayerEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.minigame.KitsModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
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

    override val dependencies = listOf(WinModule::class, SidebarModule::class)

    private val armorLevels = hashMapOf<Player, Int>()
    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent

        val binding = parent.getModule<SidebarModule>().bind {
            parent.players.map { player ->
                "armor-level-${player.username}" to
                        (player.name + Component.text(": ", BRAND_COLOR_PRIMARY_2) + player.getArmorLevel().name.decorate(TextDecoration.BOLD))
            }
        }

        eventNode.addListener(GameStartEvent::class.java) {
            for (player in parent.players) {
                player.setArmorLevel(levels.size - 1)
                binding.update()
            }
        }

        eventNode.addListener(PlayerKillPlayerEvent::class.java) { event ->
            event.attacker.decrementArmorLevel()
            event.attacker.health = event.attacker.health + 10
        }
    }

    private fun Player.getArmorLevel(): KitsModule.Kit = levels[armorLevels.getOrDefault(this, levels.size - 1)]

    /**
     * Sets the player's armor level, notifies them, and gives them all the right items.
     */
    private fun Player.setArmorLevel(newLevel: Int) {
        if (newLevel <= 0) {
            parent.getModule<WinModule>().declareWinner(this)
            inventory.clear()
            return
        }
        armorLevels[this] = newLevel

        val armorLevel = levels[newLevel]

        showTitle(Title.title(armorLevel.name, armorLevel.description))

        inventory.clear()
        for (item in armorLevel.items) {
            inventory.setItemStack(item.key, item.value)
        }
    }

    private fun Player.decrementArmorLevel() {
        setArmorLevel(armorLevels.getOrDefault(this, levels.size - 1) - 1)
    }
}