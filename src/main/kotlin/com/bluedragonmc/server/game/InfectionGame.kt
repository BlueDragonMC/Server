package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.GameMode
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Paths

class InfectionGame(mapName: String) : Game("Infection", mapName) {
    private val stickItem = ItemStack.builder(Material.STICK).displayName(Component.text("Knockback Stick"))
        .lore(
            Component.text("Use this to wack your fellow survivors", NamedTextColor.GRAY).noItalic(),
            Component.text("toward the zombies!", NamedTextColor.GRAY).noItalic()
        )
        .meta { metaBuilder: ItemMeta.Builder ->
            metaBuilder.enchantment(Enchantment.KNOCKBACK, 3)
        }.build()

    init {
        // MAP
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))

        // INSTANCE
        use(SharedInstanceModule())

        // COMBAT
        use(OldCombatModule(allowDamage = false, allowKnockback = true))
        use(CustomDeathMessageModule())

        // GAMEPLAY
        use(AwardsModule())
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))
        use(
            KitsModule(
                showMenu = false, giveKitsOnStart = true, selectableKits = listOf(
                    KitsModule.Kit(
                        Component.text("Default"), "Default kit!", Material.STICK, hashMapOf(
                            0 to stickItem
                        )
                    )
                )
            )
        )
        use(MaxHealthModule(1.0F))
        use(MOTDModule(Component.translatable("game.infection.motd")))
        use(NaturalRegenerationModule())
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        val sidebarModule = SidebarModule(name)
        val bind = sidebarModule.bind {
            players.map {
                "player-status-${it.uuid}" to it.name.withColor(
                    if (hasModule<InfectionModule>() && getModule<InfectionModule>().isInfected(it))
                        NamedTextColor.RED else NamedTextColor.GREEN
                )
            }
        }
        use(SpawnpointModule(SpawnpointModule.DatabaseSpawnpointProvider(allowRandomOrder = true)))
        use(TeamModule(autoTeams = false))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(TimedRespawnModule(5))
        use(WorldPermissionsModule(allowBlockInteract = true))

        // MINIGAME
        use(CountdownModule(threshold = 2, allowMoveDuringCountdown = true))
        use(WinModule(winCondition = WinModule.WinCondition.MANUAL) { player, winningTeam ->
            if (player in winningTeam.players) 100 else 10
        })

        use(InfectionModule(scoreboardBinding = bind))

        ready()
    }
}