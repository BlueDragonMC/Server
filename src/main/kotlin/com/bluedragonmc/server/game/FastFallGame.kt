package com.bluedragonmc.server.game

import com.bluedragonmc.server.ALT_COLOR_2
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.FallDamageModule
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import java.time.Duration
import kotlin.math.pow
import kotlin.math.sqrt

class FastFallGame(mapName: String?) : Game("FastFall", "Chaos") {
    private val radius = (38..55).random()

    private val blockSet = FastFallBlockSet.values().random()

    init {
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
        use(HealthDisplayModule())
        use(InstantRespawnModule())
        use(MaxHealthModule(2.0F))
        use(MOTDModule(Component.translatable("game.fastfall.motd")))
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(SidebarModule(name))
        use(
            SpawnpointModule(
                SpawnpointModule.TestSpawnpointProvider(
                    Pos(-4.5, 257.0, -4.5),
                    Pos(-4.5, 257.0, 4.5),
                    Pos(4.5, 257.0, -5.5),
                    Pos(-4.5, 257.0, -4.5)
                )
            )
        )
        use(VoidDeathModule(threshold = 0.0, respawnMode = true))
        use(WorldPermissionsModule(exceptions = listOf(Block.GLASS)))
        use(CustomDeathMessageModule())
        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(WinModule.WinnerDeclaredEvent::class.java) { event ->
                    event.winningTeam.players.forEach {
                        if (it.health == it.maxHealth) parent.getModule<AwardsModule>().awardCoins(
                            it,
                            50,
                            Component.translatable("game.fastfall.award.no_damage_taken", ALT_COLOR_2)
                        )
                    }
                }
            }

        })

        // MINIGAME MODULES
        use(CountdownModule(threshold = 1, allowMoveDuringCountdown = false))
        use(WinModule(winCondition = WinModule.WinCondition.TOUCH_EMERALD) { player, winningTeam ->
            if (player in winningTeam.players) 150 else 15
        })

        // SIDEBAR DISPLAY
        val binding = getModule<SidebarModule>().bind {
            players.map {
                "player-y-${it.username}" to it.name + Component.text(
                    ": ",
                    BRAND_COLOR_PRIMARY_2
                ) + Component.text("${it.position.y.toInt() - (257 - 2 * radius)}", BRAND_COLOR_PRIMARY_1)
            }
        }
        MinecraftServer.getSchedulerManager().buildTask {
            binding.update()
        }.repeat(Duration.ofSeconds(1)).schedule()

        ready()

        // TODO action bar with the player's progress to the bottom and # of players ahead
    }

    class ChaosWorldGenerator(val radius: Int, val blockSet: FastFallBlockSet) : Generator {
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
                                x,
                                y,
                                z,
                                if ((1..23).random() == 1) Block.SLIME_BLOCK else randomBlock()
                            )
                        }
                    }
                }
            }
        }

        private fun randomBlock(): Block {
            return blockSet.randomBlock()
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

    // TODO - make this use a config file
    enum class FastFallBlockSet(val blocks: Set<Block>) {
        CLASSIC(
            setOf(
                Block.RED_BED,
                Block.OAK_PRESSURE_PLATE,
                Block.OAK_PLANKS,
                Block.STONE,
                Block.DIAMOND_BLOCK,
                Block.SEA_LANTERN,
                Block.GLOWSTONE,
                Block.DIRT,
                Block.OAK_LOG,
                Block.IRON_TRAPDOOR,
                Block.NETHER_PORTAL,
                Block.PUMPKIN,
                Block.WHITE_STAINED_GLASS,
                Block.BEACON,
                Block.OBSIDIAN,
                Block.SANDSTONE,
                Block.HAY_BLOCK,
                Block.CHEST,
                Block.PISTON,
                Block.MELON,
                Block.ANDESITE,
                Block.STONE_SLAB,
            )
        ),
        NETHER(
            setOf(
                Block.BLACKSTONE,
                Block.GILDED_BLACKSTONE,
                Block.SOUL_SOIL,
                Block.SOUL_SAND,
                Block.MAGMA_BLOCK,
                Block.WARPED_NYLIUM,
                Block.CRIMSON_NYLIUM,
                Block.NETHER_BRICKS,
                Block.NETHER_BRICK_FENCE,
                Block.RED_NETHER_BRICKS,
                Block.OBSIDIAN,
                Block.CRYING_OBSIDIAN,
                Block.NETHERRACK,
                Block.WARPED_PLANKS,
                Block.CRIMSON_PLANKS,
                Block.NETHER_WART_BLOCK,
                Block.NETHERITE_BLOCK,
                Block.NETHER_GOLD_ORE,
                Block.NETHER_QUARTZ_ORE,
                Block.DEEPSLATE,
                Block.COBBLED_DEEPSLATE,
                Block.POLISHED_DEEPSLATE,
            )
        ),
        COLORS(
            setOf(
                Block.WHITE_CONCRETE,
                Block.ORANGE_CONCRETE,
                Block.MAGENTA_CONCRETE,
                Block.LIGHT_BLUE_CONCRETE,
                Block.YELLOW_CONCRETE,
                Block.LIME_CONCRETE,
                Block.PINK_CONCRETE,
                Block.GRAY_CONCRETE,
                Block.LIGHT_GRAY_CONCRETE,
                Block.CYAN_CONCRETE,
                Block.PURPLE_CONCRETE,
                Block.BLUE_CONCRETE,
                Block.BROWN_CONCRETE,
                Block.GREEN_CONCRETE,
                Block.RED_CONCRETE,
                Block.BLACK_CONCRETE,
                Block.WHITE_WOOL,
                Block.ORANGE_WOOL,
                Block.MAGENTA_WOOL,
                Block.LIGHT_BLUE_WOOL,
                Block.YELLOW_WOOL,
                Block.LIME_WOOL,
                Block.PINK_WOOL,
                Block.GRAY_WOOL,
                Block.LIGHT_GRAY_WOOL,
                Block.CYAN_WOOL,
                Block.BLUE_WOOL,
                Block.BROWN_WOOL,
                Block.GREEN_WOOL,
                Block.RED_WOOL,
                Block.BLACK_WOOL,
                Block.WHITE_STAINED_GLASS,
                Block.ORANGE_STAINED_GLASS,
                Block.MAGENTA_STAINED_GLASS,
                Block.LIGHT_BLUE_STAINED_GLASS,
                Block.YELLOW_STAINED_GLASS,
                Block.LIME_STAINED_GLASS,
                Block.PINK_STAINED_GLASS,
                Block.GRAY_STAINED_GLASS,
                Block.LIGHT_GRAY_STAINED_GLASS,
                Block.CYAN_STAINED_GLASS,
                Block.BLUE_STAINED_GLASS,
                Block.BROWN_STAINED_GLASS,
                Block.GREEN_STAINED_GLASS,
                Block.RED_STAINED_GLASS,
                Block.BLACK_STAINED_GLASS,
            )
        );

        fun randomBlock(): Block = blocks.random()
    }
}