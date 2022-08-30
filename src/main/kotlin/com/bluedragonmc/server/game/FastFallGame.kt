package com.bluedragonmc.server.game

import com.bluedragonmc.server.ALT_COLOR_2
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.gameplay.MaxHealthModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.gameplay.WorldPermissionsModule
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.FallDamageModule
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.withTransition
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.math.pow
import kotlin.math.sqrt

class FastFallGame(mapName: String?) : Game("FastFall", "Chaos") {
    private val radius = (38..55).random()

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
        var lead: Player? = null
        var lastLeadChange = 0L
        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(WinModule.WinnerDeclaredEvent::class.java) { event ->
                    event.winningTeam.players.forEach {
                        if (it.health == it.maxHealth) parent.getModule<AwardsModule>().awardCoins(
                            it, 50, Component.translatable("game.fastfall.award.no_damage_taken", ALT_COLOR_2)
                        )
                    }
                }
                eventNode.addListener(InstanceTickEvent::class.java) { event ->
                    if (event.instance.worldAge % 5 != 0L) return@addListener // Only run every 5 ticks
                    val ordered = players.sortedBy { it.position.y }
                    if (ordered.isEmpty()) return@addListener
                    val lowest = ordered.first()
                    // Players' velocity has a Y component of -1.568 when standing still
                    // Lead changes are limited to, at most, every 1.5 seconds
                    if (lead != lowest && lowest.velocity.y >= -1.568 && event.instance.worldAge - lastLeadChange > 30) {
                        if (lead != null) {
                            val msg = lowest.name + Component.text(" is now in the lead!", BRAND_COLOR_PRIMARY_2)
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
                        val playersAhead = Component.text(
                            ahead, BRAND_COLOR_PRIMARY_1
                        ) + Component.text(
                            " player${if (ahead == 1) "" else "s"} ahead · ", BRAND_COLOR_PRIMARY_2
                        )
                        val health = Component.text(String.format("%.1f%%", player.health / player.maxHealth * 100))
                            .withTransition(
                                player.health / player.maxHealth,
                                NamedTextColor.RED,
                                NamedTextColor.YELLOW,
                                NamedTextColor.GREEN
                            ) + Component.text(" health · ")
                        val yPos = Component.text(
                            player.position.y.toInt() - (257 - 2 * radius),
                            BRAND_COLOR_PRIMARY_1
                        ) + Component.text(" blocks left", BRAND_COLOR_PRIMARY_2)
                        player.sendActionBar(playersAhead + health + yPos)
                    }
                }
                eventNode.addListener(PlayerMoveEvent::class.java) { event ->
                    if (
                        event.player.instance?.getBlock(event.player.position.sub(0.0, 0.2, 0.0)) == Block.EMERALD_BLOCK &&
                        event.player.isOnGround
                    ) {
                        getModule<WinModule>().declareWinner(event.player)
                    }
                }
            }
        })

        // MINIGAME MODULES
        use(CountdownModule(threshold = 1, allowMoveDuringCountdown = false))
        use(WinModule(winCondition = WinModule.WinCondition.MANUAL) { player, winningTeam ->
            if (player in winningTeam.players) 150 else 15
        })

        // SIDEBAR DISPLAY
        val binding = getModule<SidebarModule>().bind {
            players.map {
                "player-y-${it.username}" to it.name + Component.text(
                    ": ", BRAND_COLOR_PRIMARY_2
                ) + Component.text("${it.position.y.toInt() - (257 - 2 * radius)}", BRAND_COLOR_PRIMARY_1)
            }
        }
        MinecraftServer.getSchedulerManager().buildTask {
            binding.update()
        }.repeat(Duration.ofMillis(500)).schedule()

        ready()
    }

    class ChaosWorldGenerator(val radius: Int, val blockSet: Collection<Block>) : Generator {
        override fun generate(unit: GenerationUnit) {
            val start = unit.absoluteStart()
            val end = unit.absoluteEnd()
            unit.fork { setter ->
                for (x in start.x().toInt() until end.x().toInt()) {
                    for (y in start.y().toInt() until end.y().toInt()) {
                        for (z in start.z().toInt() until end.z().toInt()) {
                            if (y == 256 - 2 * radius && pointInCircle(
                                    x.toDouble(), z.toDouble(), 0, 0, 10
                                )
                            ) setter.setBlock(x, y, z, Block.EMERALD_BLOCK) // Win point
                            else if (y == 256 - 2 * radius + 1 && pointInCircle(
                                    x.toDouble(), z.toDouble(), 0, 0, 10
                                )
                            ) setter.setBlock(x, y, z, Block.GLASS) // Breakable glass over win point
                            else if ((x == -5 || x == 5) && y == 256 && (z == -5 || z == 5)) setter.setBlock(
                                x, y, z, Block.BEDROCK
                            ) // Spawn point
                            else if (!pointInSphere(Vec(x.toDouble(), y.toDouble(), z.toDouble()), radius)) continue
                            else if ((1..20).random() == 1) setter.setBlock(
                                x, y, z, if ((1..23).random() == 1) Block.SLIME_BLOCK else randomBlock()
                            )
                        }
                    }
                }
            }
        }

        private fun randomBlock(): Block {
            return blockSet.random()
        }

        /**
         * Checks if the point is in the sphere with specified radius centered at 0, 256-radius, 0.
         */
        private fun pointInSphere(point: Point, radius: Int): Boolean {
            val distance = sqrt(point.x().pow(2) + (point.y() - (256 - radius)).pow(2) + point.z().pow(2))
            return distance <= radius
        }

        /**
         * Checks if the point (pointX, pointY) is inside of the circle with center (centerX, centerY).
         */
        private fun pointInCircle(pointX: Double, pointY: Double, centerX: Int, centerY: Int, radius: Int): Boolean {
            val distance = sqrt((pointX - centerX).pow(2) + (pointY - centerY).pow(2))
            return distance <= radius
        }

    }
}