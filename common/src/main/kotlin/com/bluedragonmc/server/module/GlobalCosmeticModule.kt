package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.database.CosmeticsModule
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

@DependsOn(CosmeticsModule::class)
class GlobalCosmeticModule : GameModule() {

    private lateinit var cosmetics: CosmeticsModule

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        cosmetics = parent.getModule<CosmeticsModule>()
    }

    fun getFireworkColor(player: Player) = cosmetics.getCosmeticInGroup<WinFireworks>(player)?.fireworkColors ?: emptyArray()

    enum class WinFireworks(override val id: String, vararg val fireworkColors: TextColor) : CosmeticsModule.Cosmetic {
        RED("global_firework_red", NamedTextColor.RED),
        ORANGE("global_firework_orange", TextColor.color(239, 147, 43)),
        YELLOW("global_firework_yellow", NamedTextColor.YELLOW),
        GREEN("global_firework_green", NamedTextColor.GREEN),
        BLUE("global_firework_blue", TextColor.color(79, 185, 242)),
        PURPLE("global_firework_purple", TextColor.color(131, 71, 229)),
        RAINBOW("global_firework_rainbow", *NamedTextColor.NAMES.values().toTypedArray()),
    }
}