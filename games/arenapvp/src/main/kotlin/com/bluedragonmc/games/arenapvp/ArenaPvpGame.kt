package com.bluedragonmc.games.arenapvp

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.KitSelectedEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.NaturalRegenerationModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerRespawnEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import java.nio.file.Paths

// TODO - Lava damage
class ArenaPvpGame(mapName: String) : Game("ArenaPvP", mapName) {
    override val maxPlayers: Int = 16

    private val jumpBoost = Potion(PotionEffect.JUMP_BOOST, 1, Int.MAX_VALUE)

    init {

        val config = use(ConfigModule("arenapvp.yml")).getConfig()
        val kits = config.node("kits").getList(KitsModule.Kit::class.java)!!

        // COMBAT
        use(CustomDeathMessageModule())
        use(OldCombatModule())

        // DATABASE
        use(AwardsModule())

        // GAMEPLAY
        use(DoubleJumpModule(verticalStrength = 20.0, cooldownMillis = 3000))
        use(GuiModule())
        use(InstantRespawnModule())
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = true))
        use(
            KitsModule(
                showMenu = true, giveKitsOnSelect = true, selectableKits = kits
            )
        )
        use(MOTDModule(Component.translatable("game.arenapvp.motd")))
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
        dependingOn(KitsModule::class) {
            handleEvent<PlayerRespawnEvent> { event ->
                getModule<KitsModule>().selectKit(event.player)
            }

            handleEvent<PlayerSpawnEvent> { event ->
                getModule<KitsModule>().giveKit(event.player)
                event.player.isAllowFlying = true
            }

            handleEvent<KitSelectedEvent> { event ->
                event.player.clearEffects()
                if (event.kit.hasAbility("jump_boost")) {
                    event.player.addEffect(jumpBoost)
                }
            }
        }

        handleEvent<OldCombatModule.PlayerAttackEvent> { event ->
            event.isCancelled = event.target.position.y > 111
        }

        handleEvent<DoubleJumpModule.PlayerDoubleJumpEvent> { event ->
            if (event.player.position.y <= 111) {
                if (!getModule<KitsModule>().getSelectedKit(event.player).hasAbility("double_jump")) {
                    event.isCancelled = true
                    event.player.isAllowFlying = false
                }
            }
        }

        handleEvent<PlayerLeaveGameEvent> { event ->
            event.player.clearEffects()
        }

        getModule<NPCModule>().addNPC(
            position = Pos(-31.5, 127.0, 284.5, -135.0f, 0.0f),
            customName = Component.translatable("game.arenapvp.npc.change_kit", ALT_COLOR_1, TextDecoration.BOLD),
            entityType = EntityType.VILLAGER,
            interaction = { getModule<KitsModule>().selectKit(it.player) },
            lookAtPlayer = false,
            enableFullSkin = false,
        )

        use(StatisticsModule())

        ready()
    }
}