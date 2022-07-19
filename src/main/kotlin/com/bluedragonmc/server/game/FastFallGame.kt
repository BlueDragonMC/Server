package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import net.minestom.server.utils.NamespaceID
import java.time.Duration
import kotlin.math.pow
import kotlin.math.sqrt

class FastFallGame(mapName: String?) : Game("FastFall", "Chaos") {
    init {
        // INSTANCE MODULES
        use(
            CustomGeneratorInstanceModule(
                dimensionType = MinecraftServer.getDimensionTypeManager().getDimension(
                    NamespaceID.from("bluedragon:fullbright_dimension")
                )!!, generator = ChaosWorldGenerator()
            )
        )

        // GAMEPLAY MODULES
        use(AwardsModule())
        use(FallDamageModule)
        use(InstantRespawnModule())
        use(MaxHealthModule(2.0F))
        use(MOTDModule(Component.text("It's a race to the bottom!\nBe the first to touch the emerald\n, but be careful! You only have one heart!")))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SidebarModule(name))
        use(SpawnpointModule(SpawnpointModule.SingleSpawnpointProvider(Pos(0.5, 258.0, 0.5))))
        use(VoidDeathModule(threshold = 0.0, respawnMode = true))
        use(WorldPermissionsModule())

        // MINIGAME MODULES
        use(CountdownModule(threshold = 1, allowMoveDuringCountdown = false))
        use(WinModule(winCondition = WinModule.WinCondition.TOUCH_EMERALD) { player, winningTeam ->
            if (player in winningTeam.players) 150 else 15
        })

        // SIDEBAR DISPLAY
        val binding = getModule<SidebarModule>().bind {
            players.map {
                "player-y-${it.username}" to it.name.append(Component.text(": ${it.position.y.toInt()}"))
            }
        }
        MinecraftServer.getSchedulerManager().buildTask {
            binding.update()
        }.repeat(Duration.ofSeconds(1)).schedule()

        ready()

        // TODO action bar with the player's progress to the bottom and # of players ahead
    }

    class ChaosWorldGenerator : Generator {
        override fun generate(unit: GenerationUnit) {
            val start = unit.absoluteStart()
            val end = unit.absoluteEnd()
            val radius = 32
            unit.fork { setter ->
                for (x in start.x().toInt() until end.x().toInt()) {
                    for (y in start.y().toInt() until end.y().toInt()) {
                        for (z in start.z().toInt() until end.z().toInt()) {
                            if (y == 256 - 2 * radius && pointInCircle(
                                    x.toDouble(),
                                    z.toDouble(),
                                    0,
                                    0,
                                    10
                                )
                            ) setter.setBlock(x, y, z, Block.EMERALD_BLOCK) // Win point
                            else if (y == 256 - 2 * radius + 4 && pointInCircle(
                                    x.toDouble(),
                                    z.toDouble(),
                                    0,
                                    0,
                                    10
                                )
                            ) setter.setBlock(x, y, z, Block.GLASS)
                            else if (x == 0 && y == 256 && z == 0) setter.setBlock(
                                x,
                                y,
                                z,
                                Block.BEDROCK
                            ) // Spawn point
                            else if (!pointInSphere(Vec(x.toDouble(), y.toDouble(), z.toDouble()), radius)) continue
                            else if ((1..20).random() == 1) setter.setBlock(x, y, z, randomBlock())
                        }
                    }
                }
            }
        }

        private fun randomBlock(): Block {
            return setOf(
                Block.SLIME_BLOCK,
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
                Block.GLASS,
                Block.BEACON,
                Block.OBSIDIAN,
                Block.SANDSTONE,
                Block.HAY_BLOCK,
                Block.CHEST,
                Block.PISTON,
                Block.MELON,
                Block.ANDESITE,
                Block.STONE_SLAB
            ).random()
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