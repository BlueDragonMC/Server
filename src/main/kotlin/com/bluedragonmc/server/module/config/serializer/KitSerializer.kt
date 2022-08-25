package com.bluedragonmc.server.module.config.serializer

import com.bluedragonmc.server.module.minigame.KitsModule
import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.text.Component
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.utils.inventory.PlayerInventoryUtils
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

class KitSerializer : TypeSerializer<KitsModule.Kit> {
    override fun deserialize(type: Type?, node: ConfigurationNode): KitsModule.Kit {
        val name = node.node("name").get<Component>()?.noItalic() ?: Component.empty()
        val description = node.node("description").string?.replace("\\n", "\n") ?: ""
        val icon = node.node("icon").get<Material>() ?: Material.DIAMOND
        val items = node.node("items").get<Map<String, ItemStack>>()!!.map { (str, itemStack) ->
            val slot = str.toIntOrNull() ?: when(str) {
                "helmet" -> PlayerInventoryUtils.HELMET_SLOT
                "chestplate" -> PlayerInventoryUtils.CHESTPLATE_SLOT
                "leggings" -> PlayerInventoryUtils.LEGGINGS_SLOT
                "boots" -> PlayerInventoryUtils.BOOTS_SLOT
                else -> error("Unknown slot preset: $str")
            }
            return@map slot to itemStack
        }
        val abilities = node.node("abilities").getList(String::class.java) ?: emptyList()
        return KitsModule.Kit(name, description, icon, hashMapOf(*items.toTypedArray()), abilities)
    }

    override fun serialize(type: Type?, obj: KitsModule.Kit?, node: ConfigurationNode) {
        TODO("Serialization of KitsModule.Kit not implemented.")
    }
}