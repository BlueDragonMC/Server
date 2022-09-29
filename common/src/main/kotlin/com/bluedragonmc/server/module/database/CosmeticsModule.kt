package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.ConfigModule
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.spongepowered.configurate.ConfigurationNode

@DependsOn(DatabaseModule::class, ConfigModule::class)
class CosmeticsModule : GameModule() {

    lateinit var config: ConfigurationNode

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        config = parent.getModule<ConfigModule>().loadExtra("cosmetics.yml") ?: error("Failed to load cosmetics configuration.")
    }

    fun isCosmeticEquipped(player: Player, cosmetic: Cosmetic): Boolean {
        return (player as CustomPlayer).data.cosmetics.filter {
            it.id == cosmetic.id
        }.any { it.equipped }
    }

    fun hasCosmetic(player: Player, cosmetic: Cosmetic): Boolean {
        return (player as CustomPlayer).data.cosmetics.any {
            it.id == cosmetic.id
        }
    }

    inline fun <reified T : Cosmetic> getCosmeticInGroup(player: Player): T? {
        T::class.java.enumConstants.forEach { cosmetic ->
            if (isCosmeticEquipped(player, cosmetic)) return cosmetic
        }
        return null // No cosmetic in this group is equipped
    }

    interface Cosmetic {
        val id: String
    }
}