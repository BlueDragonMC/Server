package com.bluedragonmc.server.game

import com.bluedragonmc.server.*
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.utils.ItemUtils
import com.bluedragonmc.server.utils.ItemUtils.withArmorColor
import com.bluedragonmc.server.utils.ItemUtils.withEnchant
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.withColor
import com.bluedragonmc.server.utils.withGradient
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerRespawnEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.utils.inventory.PlayerInventoryUtils
import java.nio.file.Paths

// TODO - Lava damage
class ArenaPvpGame(mapName: String) : Game("ArenaPvP", mapName) {
    override val maxPlayers: Int = 16
    private val cactusColor = Color(0, 255, 0)
    private val blazeColor = Color(255, 100, 0)

    private val jumpBoost = Potion(PotionEffect.JUMP_BOOST, 1, Int.MAX_VALUE)

    private val kits = mutableListOf<KitsModule.Kit>()

    private val classicKit = KitsModule.Kit(
        ("Classic" withColor NamedTextColor.YELLOW).noItalic(),
        "Just the usual armor and sword.",
        Material.IRON_CHESTPLATE,
        hashMapOf(
            0 to ItemStack.of(Material.IRON_SWORD),
            PlayerInventoryUtils.HELMET_SLOT to ItemStack.of(Material.IRON_HELMET),
            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.of(Material.IRON_CHESTPLATE),
            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.of(Material.IRON_LEGGINGS),
            PlayerInventoryUtils.BOOTS_SLOT to ItemStack.of(Material.IRON_BOOTS),
        )
    ).apply { kits.add(this) }
    private val kotmKit = KitsModule.Kit(
        Component.text("Kit of the Month").withGradient(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_2).noItalic(),
        "What will it be for August?",
        Material.AMETHYST_BLOCK,
        hashMapOf(
            0 to ItemStack.of(Material.NETHERITE_SWORD),
            PlayerInventoryUtils.HELMET_SLOT to ItemStack.of(Material.LEATHER_HELMET),
            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.of(Material.LEATHER_CHESTPLATE),
            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.of(Material.LEATHER_LEGGINGS),
            PlayerInventoryUtils.BOOTS_SLOT to ItemStack.of(Material.LEATHER_BOOTS),
        )
    ).apply { kits.add(this) }
    private val trollKit = KitsModule.Kit(
        ("Troll" withColor NamedTextColor.RED).noItalic(),
        "Receive a knockback stick to wack your enemies!",
        Material.STICK,
        hashMapOf(
            0 to ItemUtils.knockbackStick(5),
            1 to ItemStack.of(Material.WOODEN_SWORD),
            PlayerInventoryUtils.HELMET_SLOT to ItemStack.of(Material.IRON_HELMET),
            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.of(Material.IRON_CHESTPLATE),
            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.of(Material.IRON_LEGGINGS),
            PlayerInventoryUtils.BOOTS_SLOT to ItemStack.of(Material.IRON_BOOTS),
        )
    ).apply { kits.add(this) }
    private val cactusKit = KitsModule.Kit(
        ("Cactus" withColor NamedTextColor.GREEN).noItalic(),
        "Your armor has thorns, which hurts\nother players when they attack you.",
        Material.CACTUS,
        hashMapOf(
            0 to ItemStack.of(Material.IRON_SWORD),
            PlayerInventoryUtils.HELMET_SLOT to ItemStack.of(Material.LEATHER_HELMET).withEnchant(Enchantment.THORNS, 2)
                .withArmorColor(cactusColor),
            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.of(Material.LEATHER_CHESTPLATE)
                .withEnchant(Enchantment.THORNS, 2).withArmorColor(cactusColor),
            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.of(Material.LEATHER_LEGGINGS)
                .withEnchant(Enchantment.THORNS, 2).withArmorColor(cactusColor),
            PlayerInventoryUtils.BOOTS_SLOT to ItemStack.of(Material.LEATHER_BOOTS).withEnchant(Enchantment.THORNS, 2)
                .withArmorColor(cactusColor),
        )
    ).apply { kits.add(this) }
//    private val blazeKit = KitsModule.Kit(
//        ("Blaze" withColor NamedTextColor.GOLD).noItalic(),
//        "You set other players on fire when you attack them.",
//        Material.BLAZE_ROD,
//        hashMapOf(
//            0 to ItemStack.of(Material.BLAZE_ROD).withEnchant(Enchantment.SHARPNESS, 3)
//                .withEnchant(Enchantment.FIRE_ASPECT, 2),
//            PlayerInventoryUtils.HELMET_SLOT to ItemStack.of(Material.LEATHER_HELMET)
//                .withEnchant(Enchantment.PROTECTION, 2).withArmorColor(blazeColor),
//            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.of(Material.LEATHER_CHESTPLATE)
//                .withEnchant(Enchantment.PROTECTION, 2).withArmorColor(blazeColor),
//            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.of(Material.LEATHER_LEGGINGS)
//                .withEnchant(Enchantment.PROTECTION, 2).withArmorColor(blazeColor),
//            PlayerInventoryUtils.BOOTS_SLOT to ItemStack.of(Material.LEATHER_BOOTS)
//                .withEnchant(Enchantment.PROTECTION, 2).withArmorColor(blazeColor),
//        )
//    ).apply { kits.add(this) }
    private val lunarKit = KitsModule.Kit(
        ("Moonwalker" withColor NamedTextColor.AQUA).noItalic(),
        "You jump higher, and you can double jump.",
        Material.END_STONE,
        hashMapOf(
            0 to ItemStack.of(Material.STONE_SWORD),
            PlayerInventoryUtils.HELMET_SLOT to ItemStack.of(Material.CHAINMAIL_HELMET),
            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.of(Material.CHAINMAIL_CHESTPLATE),
            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.of(Material.CHAINMAIL_LEGGINGS),
            PlayerInventoryUtils.BOOTS_SLOT to ItemStack.of(Material.CHAINMAIL_BOOTS),
        )
    ).apply { kits.add(this) }

    init {
        // COMBAT
        use(CustomDeathMessageModule())
        use(OldCombatModule())

        // DATABASE
        use(AwardsModule())

        // GAMEPLAY
        use(DoubleJumpModule(verticalStrength = 20.0))
        use(GuiModule())
        use(InstantRespawnModule())
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = true))
        use(
            KitsModule(
                showMenu = true, giveKitsOnSelect = true, selectableKits = kits
            )
        )
        use(MOTDModule(Component.text("Select your kit and drop into battle!\nAfter you die, you can pick a new kit.")))
        use(NaturalRegenerationModule())
        use(NPCModule())
        use(PlayerResetModule(GameMode.ADVENTURE))
        use(SpawnpointModule(SpawnpointModule.SingleSpawnpointProvider(Pos(-26.5, 127.0625, 280.5, 90.0f, 0.0f))))
        use(VoidDeathModule(threshold = 0.0, respawnMode = false))
        use(WorldPermissionsModule())

        // INSTANCE
        use(SharedInstanceModule())

        // MAP
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))

        // CUSTOM
        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerRespawnEvent::class.java) { event ->
                    getModule<KitsModule>().selectKit(event.player)
                }
                eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
                    event.isCancelled = event.target.position.y > 111
                }
                eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
                    getModule<KitsModule>().giveKit(event.player)
                }
                eventNode.addListener(DoubleJumpModule.PlayerDoubleJumpEvent::class.java) { event ->
                    event.isCancelled =
                        event.player.position.y <= 111 && getModule<KitsModule>().getSelectedKit(event.player) !== lunarKit
                }
                eventNode.addListener(KitsModule.KitSelectedEvent::class.java) { event ->
                    event.player.clearEffects()
                    if (event.kit === lunarKit) {
                        event.player.addEffect(jumpBoost)
                    }
                }
                eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
                    event.player.clearEffects()
                }
            }
        })

        getModule<NPCModule>().addNPC(
            position = Pos(-31.5, 127.0, 284.5, -135.0f, 0.0f),
            customName = Component.text("Change Kit", ALT_COLOR_1, TextDecoration.BOLD),
            entityType = EntityType.VILLAGER,
            interaction = { getModule<KitsModule>().selectKit(it.player) },
            lookAtPlayer = false,
            enableFullSkin = false,
        )

        ready()
    }
}