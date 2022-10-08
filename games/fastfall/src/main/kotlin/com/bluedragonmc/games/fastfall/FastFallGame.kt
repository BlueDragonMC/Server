package com.bluedragonmc.games.fastfall

import com.bluedragonmc.server.*
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GlobalCosmeticModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.FallDamageModule
import com.bluedragonmc.server.utils.*
import com.bluedragonmc.server.utils.ItemUtils.withArmorColor
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ExplosionPacket
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.properties.Delegates

class FastFallGame(mapName: String?) : Game("FastFall", mapName ?: "Chaos") {
    private val radius = (38..55).random()

    companion object {
        /**
         * Blocks that cannot be modified by the Midas cosmetic.
         */
        val indestructibleBlocks = setOf(
            Block.SLIME_BLOCK,
            Block.GLASS,
            Block.BEDROCK,
            Block.EMERALD_BLOCK
        )
    }

    init {

        val config = use(ConfigModule("fastfall.yml")).getConfig()
        val blockSetName = config.node("block-sets").childrenMap().keys.random()
        logger.info("Using block set: $blockSetName")
        val blockSet = config.node("block-sets", blockSetName).getList(Block::class.java)!!

        // INSTANCE MODULES
        use(
            CustomGeneratorInstanceModule(
                dimensionType = CustomGeneratorInstanceModule.getFullbrightDimension(),
                generator = ChaosWorldGenerator(radius, blockSet)
            )
        )

        // GAMEPLAY MODULES
        use(AwardsModule())
        use(FallDamageModule)
        use(InstantRespawnModule())
        use(MaxHealthModule(2.0F))
        use(
            MOTDModule(
                showMapName = false,
                motd = Component.translatable("game.fastfall.motd") +
                        Component.newline() +
                        Component.translatable(
                            "game.fastfall.motd.theme", BRAND_COLOR_PRIMARY_2,
                            Component.translatable("game.fastfall.block_set.${blockSetName}", BRAND_COLOR_PRIMARY_1, TextDecoration.BOLD)
                        )
            )
        )
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(SidebarModule(name))
        use(
            SpawnpointModule(
                SpawnpointModule.TestSpawnpointProvider(
                    Pos(-4.5, 257.0, -4.5), Pos(-4.5, 257.0, 5.5), Pos(5.5, 257.0, 5.5), Pos(5.5, 257.0, -4.5)
                )
            )
        )
        use(VoidDeathModule(threshold = 0.0, respawnMode = true))
        use(WorldPermissionsModule(exceptions = listOf(Block.GLASS)))
        use(CustomDeathMessageModule())
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))

        val cosmetics = use(CosmeticsModule())

        var lead: Player? = null
        var lastLeadChange = 0L
        var isSingleplayer by Delegates.notNull<Boolean>()
        var startTime: Long? = null

        handleEvent<PlayerSpawnEvent>(CosmeticsModule::class) { event ->
            event.player.boots =
                cosmetics.getCosmeticInGroup<FastFallBoots>(event.player)?.item ?: return@handleEvent
        }

        handleEvent<GameStartEvent>(CosmeticsModule::class) {
            startTime = System.currentTimeMillis()
            isSingleplayer = players.size <= 1

            if (isSingleplayer) players.forEach {
                it.sendMessage(Component.translatable("game.fastfall.singleplayer_warning", ALT_COLOR_1))
            }

            players.forEach {
                val boots = cosmetics.getCosmeticInGroup<FastFallBoots>(it)?.item
                if (boots != null) {
                    it.boots = boots
                }
            }
        }

        handleEvent<WinModule.WinnerDeclaredEvent>(StatisticsModule::class, AwardsModule::class) { event ->

            val time = System.currentTimeMillis() - startTime!!

            event.winningTeam.players.forEach { player ->
                if (player.health != player.maxHealth) return@handleEvent
                val amount = if (isSingleplayer) 10 else 50
                getModule<AwardsModule>().awardCoins(
                    player, amount, Component.translatable("game.fastfall.award.no_damage_taken", ALT_COLOR_2)
                )

                // Record the player's best time, if they beat their personal best
                getModule<StatisticsModule>().recordStatisticIfLower(player, "game_fastfall_best_time", time.toDouble()) {
                    val str = formatDuration(time)
                    player.sendMessage(Component.translatable(
                        "game.fastfall.new_record", ALT_COLOR_2, setOf(TextDecoration.BOLD),
                        Component.text(str, ALT_COLOR_1))
                    )
                    player.playSound(Sound.sound(
                        SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 1.0f, 1.0f
                    ))
                }
                if (!isSingleplayer) {
                    // Only record wins in multiplayer
                    getModule<StatisticsModule>().incrementStatistic(player, "game_fastfall_wins")
                }
            }
        }

        handleEvent<InstanceTickEvent> { event ->
            if (event.instance.worldAge % 5 != 0L) return@handleEvent // Only run every 5 ticks
            val ordered = players.sortedBy { it.position.y }
            if (ordered.isEmpty()) return@handleEvent
            val lowest = ordered.first()
            // Players' velocity has a Y component of -1.568 when standing still
            // Lead changes are limited to, at most, every 1.5 seconds
            if (lead != lowest && lowest.velocity.y >= -1.568 && event.instance.worldAge - lastLeadChange > 30) {
                if (lead != null) {
                    val msg = Component.translatable("game.fastfall.lead_changed", BRAND_COLOR_PRIMARY_2, lowest.name)
                    sendMessage(msg)
                    showTitle(Title.title(Component.empty(), msg))
                    playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.PLAYER, 1.0f, 1.0f))
                    lowest.isGlowing = true
                    lead?.isGlowing = false
                }
                lead = lowest
                lastLeadChange = event.instance.worldAge
            }
            ordered.forEachIndexed { index, player ->
                val ahead = ordered.size - (ordered.size - index)

                val key = if (ahead == 1) "game.fastfall.actionbar.singular" else "game.fastfall.actionbar"
                val actionBarComponent = Component.translatable(key, BRAND_COLOR_PRIMARY_2,
                    Component.text(ahead, BRAND_COLOR_PRIMARY_1),
                    Component.text(String.format("%.1f%%", player.health / player.maxHealth * 100))
                        .withTransition(
                            player.health / player.maxHealth,
                            NamedTextColor.RED,
                            NamedTextColor.YELLOW,
                            NamedTextColor.GREEN
                        ),
                    Component.text(
                        player.position.y.toInt() - (257 - 2 * radius),
                        BRAND_COLOR_PRIMARY_1
                    )
                )

                player.sendActionBar(actionBarComponent)
            }
        }

        handleEvent<PlayerMoveEvent> { event ->
            if (event.player.isOnGround && event.player.instance?.getBlock(
                    event.player.position.sub(0.0, 0.2, 0.0)
                ) == Block.EMERALD_BLOCK) {
                getModule<WinModule>().declareWinner(event.player)
            }
        }

        // MINIGAME MODULES
        use(CountdownModule(threshold = 1, allowMoveDuringCountdown = false))
        use(WinModule(winCondition = WinModule.WinCondition.MANUAL) { player, winningTeam ->
            if (isSingleplayer) 10
            else if (player in winningTeam.players) 150
            else 15
        })

        // SIDEBAR DISPLAY
        val binding = getModule<SidebarModule>().bind {
            val duration = Duration.ofMillis(System.currentTimeMillis() - (startTime ?: return@bind emptySet()))
            val elapsed = "_time-elapsed" to Component.translatable(
                "game.fastfall.sidebar.elapsed", BRAND_COLOR_PRIMARY_2,
                Component.text(String.format("%02d:%02d", duration.toMinutesPart(), duration.toSecondsPart()))
            )
            setOf(elapsed) + players.map {
                "player-y-${it.username}" to it.name + Component.text(
                    ": ", BRAND_COLOR_PRIMARY_2
                ) + Component.text("${it.position.y.toInt() - (257 - 2 * radius)}", BRAND_COLOR_PRIMARY_1)
            }
        }
        MinecraftServer.getSchedulerManager().buildTask {
            if (state == GameState.INGAME) binding.update()
        }.repeat(Duration.ofMillis(500)).schedule()

        use(StatisticsModule(recordWins = false))
        use(GlobalCosmeticModule())

        cosmetics.handleEvent<PlayerMoveEvent>(FastFallBoots.MIDAS) { event ->
            if (event.isOnGround) {
                val posBelow = event.player.position.sub(0.0, 0.2, 0.0)
                val blockBelow = event.player.instance?.getBlock(posBelow)
                    ?: return@handleEvent
                if (blockBelow.isFullCube() && !indestructibleBlocks.contains(blockBelow)) {
                    event.player.instance?.setBlock(posBelow, Block.GOLD_BLOCK)
                }
            }
        }

        cosmetics.handleEvent<PlayerDeathEvent>(FastFallBoots.TNT) { event ->
            val pos = event.player.position
            // Play explosion effect
            sendGroupedPacket(ExplosionPacket(
                pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat(),
                5.0f, ByteArray(0), 0.0f, 0.0f, 0.0f))
            // Play a sound
            SoundUtils.playSoundInWorld(
                Sound.sound(
                    SoundEvent.ENTITY_GENERIC_EXPLODE, Sound.Source.BLOCK, 1.0f, 1.0f
                ), getInstance(), pos
            )
        }

        ready()
    }

    enum class FastFallBoots(override val id: String, val item: ItemStack) : CosmeticsModule.Cosmetic {
        STANDARD("fastfall_boots_standard", ItemStack.of(Material.LEATHER_BOOTS)),
        NETHERITE("fastfall_boots_netherite", ItemStack.of(Material.NETHERITE_BOOTS)),
        MIDAS("fastfall_boots_gold", ItemStack.of(Material.GOLDEN_BOOTS)),
        TNT("fastfall_boots_tnt", ItemStack.of(Material.LEATHER_BOOTS).withArmorColor(Color(255, 0, 0))),
    }

}