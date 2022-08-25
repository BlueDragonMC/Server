package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.minigame.KitsModule
import com.bluedragonmc.server.utils.noItalic
import com.google.gson.reflect.TypeToken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.metadata.LeatherArmorMeta
import net.minestom.server.utils.inventory.PlayerInventoryUtils
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.serialize.ScalarSerializer
import org.spongepowered.configurate.serialize.TypeSerializer
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.util.function.Predicate

class ConfigModule(private val configFileName: String) : GameModule() {

    private lateinit var root: CommentedConfigurationNode

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {

        val prefix = "config/"
        val reader =
            BufferedReader(InputStreamReader(javaClass.classLoader.getResourceAsStream(prefix + configFileName)!!))
        val loader = YamlConfigurationLoader.builder()
            .source { reader }
            .build()

        val config = ConfigurationOptions.defaults().serializers { builder ->
            builder.register(Pos::class.java, object : ScalarSerializer<Pos>(Pos::class.java) {
                override fun deserialize(type: Type?, obj: Any?): Pos {
                    val string = obj.toString()
                    val split = string.split(",").map { it.trim().toDouble() }
                    return when (split.size) {
                        3 -> Pos(split[0], split[1], split[2])
                        5 -> Pos(split[0], split[1], split[2], split[3].toFloat(), split[4].toFloat())
                        else -> error("Invalid number of elements: ${split.size}: expected 3 or 5")
                    }
                }

                override fun serialize(item: Pos?, typeSupported: Predicate<Class<*>>?): Any {
                    return item?.run { "$x,$y,$z,$yaw,$pitch" } ?: error("Cannot serialize null Pos")
                }
            })
            builder.register(Color::class.java, object : ScalarSerializer<Color>(Color::class.java) {
                override fun deserialize(type: Type?, obj: Any?): Color {
                    val string = obj.toString()
                    val split = string.split(",").map { it.trim().toInt() }
                    return when (split.size) {
                        3 -> Color(split[0], split[1], split[2])
                        else -> error("Invalid number of elements: ${split.size}: expected 3")
                    }
                }

                override fun serialize(item: Color?, typeSupported: Predicate<Class<*>>?): Any {
                    return item?.run { "${red()},${green()},${blue()}" } ?: error("Cannot serialize null Color")
                }
            })
            builder.register(Component::class.java, object : ScalarSerializer<Component>(Component::class.java) {
                override fun deserialize(type: Type?, obj: Any?): Component {
                    val string = obj.toString()
                    return MiniMessage.miniMessage().deserialize(string)
                }

                override fun serialize(item: Component?, typeSupported: Predicate<Class<*>>?): Any {
                    return MiniMessage.miniMessage().serialize(item ?: Component.empty())
                }
            })
            builder.register(EntityType::class.java, object : ScalarSerializer<EntityType>(EntityType::class.java) {
                override fun deserialize(type: Type?, obj: Any?): EntityType {
                    val string = obj.toString()
                    return EntityType.fromNamespaceId(string)
                }

                override fun serialize(item: EntityType?, typeSupported: Predicate<Class<*>>?): Any? {
                    return item?.namespace()?.asString()
                }
            })
            builder.register(Material::class.java, object : ScalarSerializer<Material>(Material::class.java) {
                override fun deserialize(type: Type?, obj: Any?): Material? {
                    val string = obj.toString()
                    return Material.fromNamespaceId(string)
                }

                override fun serialize(item: Material?, typeSupported: Predicate<Class<*>>?): Any? {
                    return item?.namespace()?.asString()
                }
            })
            builder.register(Enchantment::class.java, object : ScalarSerializer<Enchantment>(Enchantment::class.java) {
                override fun deserialize(type: Type?, obj: Any?): Enchantment? {
                    val string = obj.toString()
                    return Enchantment.fromNamespaceId(string)
                }

                override fun serialize(item: Enchantment?, typeSupported: Predicate<Class<*>>?): Any? {
                    return item?.namespace()?.asString()
                }
            })
            builder.register(PlayerSkin::class.java, object : TypeSerializer<PlayerSkin> {
                override fun deserialize(type: Type?, node: ConfigurationNode?): PlayerSkin {
                    val textures = node?.node("textures")?.string
                    val signature = node?.node("signature")?.string
                    return PlayerSkin(textures, signature)
                }

                override fun serialize(type: Type?, obj: PlayerSkin?, node: ConfigurationNode?) {
                    node?.node("textures")?.set(obj?.textures())
                    node?.node("signature")?.set(obj?.signature())
                }
            })
            builder.register(KitsModule.Kit::class.java, object : TypeSerializer<KitsModule.Kit> {
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
            })
            builder.register(ItemStack::class.java, object : TypeSerializer<ItemStack> {
                override fun deserialize(type: Type?, node: ConfigurationNode): ItemStack {
                    val material = node.node("material").get<Material>() ?: error("No material present")
                    val amount = node.node("amount").getInt(1)
                    val enchType = TypeToken.getParameterized(Map::class.java, Enchantment::class.java, Short::class.javaObjectType).type
                    @Suppress("UNCHECKED_CAST") val enchantments = node.node("enchants").get(enchType) as Map<Enchantment, Short>
                    val name = node.node("name").get<Component>()?.noItalic()
                    val lore = node.node("lore").getList(Component::class.java)
                    val dye = node.node("dye").get<Color>()

                    val itemStack = ItemStack.builder(material).run {

                        amount(amount)

                        meta { builder ->
                            if (enchantments != null) builder.enchantments(enchantments)
                            if (name != null) builder.displayName(name)
                            if (lore != null) builder.lore(*lore.toTypedArray())
                        }
                        build()
                    }

                    if(dye != null) return itemStack.withMeta(LeatherArmorMeta::class.java) { it.color(dye) }

                    return itemStack
                }

                override fun serialize(type: Type?, obj: ItemStack?, node: ConfigurationNode?) {
                    node?.node("material")?.set(obj?.material())
                    node?.node("amount")?.set(obj?.amount())
                    node?.node("enchants")?.set(obj?.meta()?.enchantmentMap)
                    node?.node("name")?.set(obj?.displayName)
                    node?.node("lore")?.set(obj?.lore)
                }
            })
        }
        root = loader.load(config)
    }

    fun getConfig(): CommentedConfigurationNode {
        return root
    }
}