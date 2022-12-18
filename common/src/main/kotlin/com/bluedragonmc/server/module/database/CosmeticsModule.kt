package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.model.CosmeticEntry
import com.bluedragonmc.server.model.PlayerDocument
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.ConfigModule
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.function.Consumer
import kotlin.reflect.KClass

@DependsOn(ConfigModule::class)
class CosmeticsModule : GameModule() {

    private lateinit var parent: Game

    companion object {
        private lateinit var config: ConfigurationNode

        private lateinit var categories: List<Category>
        private lateinit var cosmetics: List<CosmeticEntry>
        private lateinit var cosmeticsById: Map<String, CosmeticEntry>

        private fun isConfigLoaded() = ::config.isInitialized
    }

    @ConfigSerializable
    data class Category(
        val id: String = "",
        val name: Component = Component.empty(),
        val material: Material = Material.AIR,
        val groups: List<Group> = emptyList()
    )

    @ConfigSerializable
    data class Group(
        val id: String = "",
        val name: Component = Component.empty(),
        val description: Component = Component.empty(),
        val material: Material = Material.AIR,
        val cosmetics: List<CosmeticEntry> = emptyList()
    )

    @ConfigSerializable
    data class CosmeticEntry(
        val id: String = "",
        val name: Component = Component.empty(),
        val material: Material = Material.AIR,
        val item: ItemStack = ItemStack.AIR,
        val description: Component = Component.empty(),
        val cost: Int = Int.MAX_VALUE
    ) {
        val itemStack: ItemStack
            get() = if (material == Material.AIR) {
                item
            } else ItemStack.of(material)
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        if (!isConfigLoaded()) {
            config = parent.getModule<ConfigModule>().loadExtra("cosmetics.yml")
                ?: error("Failed to load cosmetics configuration.")
            categories = config.node("categories").getList(Category::class.java)!!
            cosmetics = categories.flatMap { it.groups }.flatMap { it.cosmetics }
            cosmeticsById = cosmetics.associateBy { it.id }
        }
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

    fun canAffordCosmetic(player: Player, cosmetic: Cosmetic) =
        (player as CustomPlayer).data.coins >= getCosmetic(cosmetic.id)!!.cost

    suspend fun purchaseCosmetic(player: Player, cosmetic: Cosmetic) {
        player as CustomPlayer
        val entry = getCosmetic(cosmetic.id)!!
        player.data.compute(PlayerDocument::coins) { coins -> coins - entry.cost }
        player.data.compute(PlayerDocument::cosmetics) { cosmetics ->
            val mutable = cosmetics.toMutableList()
            mutable.add(CosmeticEntry(cosmetic.id, false))
            mutable.toList()
        }
    }

    suspend fun equipCosmetic(player: Player, cosmetic: Cosmetic) {
        player as CustomPlayer
        val group = getGroupOfCosmetic(cosmetic)
        player.data.compute(PlayerDocument::cosmetics) { cosmetics ->
            cosmetics.forEach {
                // Equip the cosmetic
                if (it.id == cosmetic.id) it.equipped = true
                // Unequip other cosmetics in the group
                else if (group?.cosmetics?.any { c -> c.id == it.id } == true) it.equipped = false
            }
            cosmetics
        }
        parent.callEvent(PlayerEquipCosmeticEvent(player, cosmetic, group!!))
    }

    suspend fun unequipCosmeticsInGroup(player: Player, groupId: String) {
        player as CustomPlayer
        val group = getGroup(groupId)!!
        player.data.compute(PlayerDocument::cosmetics) { cosmetics ->
            cosmetics.forEach {
                if (group.cosmetics.any { c -> c.id == it.id } && it.equipped) {
                    it.equipped = false
                }
            }
            cosmetics
        }
        parent.callEvent(PlayerUnequipCosmeticEvent(player, group))
    }

    fun getCategories() = categories
    fun getCategory(id: String) = categories.find { it.id == id }

    fun getGroups() = categories.flatMap { it.groups }
    fun getGroup(id: String) = getGroups().find { it.id == id }

    fun getCosmetics() = cosmetics
    fun getCosmetic(id: String) = cosmeticsById[id]

    fun getGroupOfCosmetic(cosmetic: Cosmetic) = getGroups().find {
        it.cosmetics.any { c -> c.id == cosmetic.id }
    }

    private fun getEventNode(cosmetic: Cosmetic): EventNode<PlayerEvent> {
        val id = "cosmetic-${cosmetic.id}-users"
        val existing = this.eventNode.findChildren(id, PlayerEvent::class.java).firstOrNull()
        if (existing != null) {
            return existing
        }
        val eventNode = EventNode.type(id, EventFilter.PLAYER) { _, player ->
            isCosmeticEquipped(player, cosmetic)
        }
        this.eventNode.addChild(eventNode)
        return eventNode
    }

    inline fun <reified T : PlayerEvent> handleEvent(cosmetic: Cosmetic, handler: Consumer<T>) =
        handleEvent(cosmetic, T::class, handler)

    fun <T : PlayerEvent> handleEvent(cosmetic: Cosmetic, eventType: KClass<T>, handler: Consumer<T>) {
        val eventNode = getEventNode(cosmetic)
        eventNode.addListener(eventType.java, handler)
    }

    interface Cosmetic {
        val id: String
    }

    private class CosmeticImpl(override val id: String) : Cosmetic

    fun withId(id: String): Cosmetic = CosmeticImpl(id)

    class PlayerEquipCosmeticEvent(private val player: Player, val newCosmetic: Cosmetic, val newCosmeticGroup: Group) : PlayerEvent {
        override fun getPlayer(): Player = player
    }

    class PlayerUnequipCosmeticEvent(private val player: Player, val oldCosmeticGroup: Group) : PlayerEvent {
        override fun getPlayer(): Player = player
    }
}