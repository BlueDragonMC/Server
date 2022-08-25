package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.PlayerKillPlayerEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

/**
 * A module that gives an armor level to each player.
 * Whenever they get a kill ([PlayerKillPlayerEvent] is called), the killer goes down an armor level.
 * When a player gets a kill with the lowest armor level (leather), they win the game.
 * This module was designed for PvPMaster, but it can be used for other games.
 */
class ArmorLevelsModule : GameModule() {
    override val dependencies = listOf(WinModule::class) // TODO scoreboard bindings
    private val armorLevels = hashMapOf<Player, Int>()
    private lateinit var parent: Game
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(GameStartEvent::class.java) { event ->
            for (player in parent.players) {
                player.setArmorLevel(ArmorLevel.values().size - 1)
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

        val armorLevel = ArmorLevel.values()[newLevel]

        showTitle(Title.title(armorLevel.levelName, armorLevel.description))

        inventory.clear()
        inventory.helmet = armorLevel.helmet ?: ItemStack.of(Material.AIR)
        inventory.chestplate = armorLevel.chestplate ?: ItemStack.of(Material.AIR)
        inventory.leggings = armorLevel.leggings ?: ItemStack.of(Material.AIR)
        inventory.boots = armorLevel.boots ?: ItemStack.of(Material.AIR)
        inventory.setItemStack(0, armorLevel.sword ?: ItemStack.of(Material.AIR))
    }

    private fun Player.decrementArmorLevel() {
        setArmorLevel(armorLevels.getOrDefault(this, ArmorLevel.values().size - 1) - 1)
    }

    enum class ArmorLevel(
        val levelName: Component,
        val description: Component,
        val helmet: ItemStack?,
        val chestplate: ItemStack?,
        val leggings: ItemStack?,
        val boots: ItemStack?,
        val sword: ItemStack?,
    ) {
        NOTHING(
            "Nothing" withColor NamedTextColor.BLACK,
            "No armor at all!" withColor ALT_COLOR_1,
            null,
            null,
            null,
            null,
            null
        ),
        LEATHER(
            "Leather" withColor TextColor.color(0x5f3c26),
            "One kill to go!" withColor ALT_COLOR_1,
            ItemStack.of(Material.LEATHER_HELMET),
            ItemStack.of(Material.LEATHER_CHESTPLATE),
            ItemStack.of(Material.LEATHER_LEGGINGS),
            ItemStack.of(Material.LEATHER_BOOTS),
            ItemStack.of(Material.WOODEN_SWORD)
        ),
        GOLD(
            "Gold" withColor NamedTextColor.GOLD,
            "Like having no armor at all!" withColor ALT_COLOR_1,
            ItemStack.of(Material.GOLDEN_HELMET),
            ItemStack.of(Material.GOLDEN_CHESTPLATE),
            ItemStack.of(Material.GOLDEN_LEGGINGS),
            ItemStack.of(Material.GOLDEN_BOOTS),
            ItemStack.of(Material.GOLDEN_SWORD)
        ),
        CHAINMAIL(
            "Chainmail" withColor NamedTextColor.DARK_GRAY,
            "Still better than gold." withColor ALT_COLOR_1,
            ItemStack.of(Material.CHAINMAIL_HELMET),
            ItemStack.of(Material.CHAINMAIL_CHESTPLATE),
            ItemStack.of(Material.CHAINMAIL_LEGGINGS),
            ItemStack.of(Material.CHAINMAIL_BOOTS),
            ItemStack.of(Material.STONE_SWORD)
        ),
        IRON(
            "Iron" withColor NamedTextColor.WHITE,
            "Shiny!" withColor ALT_COLOR_1,
            ItemStack.of(Material.IRON_HELMET),
            ItemStack.of(Material.IRON_CHESTPLATE),
            ItemStack.of(Material.IRON_LEGGINGS),
            ItemStack.of(Material.IRON_BOOTS),
            ItemStack.of(Material.IRON_SWORD)
        ),
        DIAMOND(
            "Diamond" withColor NamedTextColor.AQUA,
            "Rock solid!" withColor ALT_COLOR_1,
            ItemStack.of(Material.DIAMOND_HELMET),
            ItemStack.of(Material.DIAMOND_CHESTPLATE),
            ItemStack.of(Material.DIAMOND_LEGGINGS),
            ItemStack.of(Material.DIAMOND_BOOTS),
            ItemStack.of(Material.DIAMOND_SWORD)
        ),
        NETHERITE(
            "Netherite" withColor TextColor.color(0x383538),
            "Get 6 kills to win!" withColor ALT_COLOR_1,
            ItemStack.of(Material.NETHERITE_HELMET),
            ItemStack.of(Material.NETHERITE_CHESTPLATE),
            ItemStack.of(Material.NETHERITE_LEGGINGS),
            ItemStack.of(Material.NETHERITE_BOOTS),
            ItemStack.of(Material.NETHERITE_SWORD)
        )
    }
}