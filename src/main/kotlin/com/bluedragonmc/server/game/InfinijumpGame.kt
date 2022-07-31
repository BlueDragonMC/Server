package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.GameState
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.SoundUtils
import com.bluedragonmc.server.utils.packet.GlowingEntityUtils
import com.bluedragonmc.server.utils.packet.PacketUtils
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

private const val blockLiveTime = 120

class InfinijumpGame(mapName: String?) : Game("Infinijump", mapName ?: "Classic") {

    private var score = 0
    override val maxPlayers = 1

    private val spawnPosition = Pos(0.5, 64.0, 0.5)

    init {
        use(VoidDeathModule(0.0))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(CountdownModule(threshold = 1, allowMoveDuringCountdown = false, countdownSeconds = 5))
        use(WinModule(WinModule.WinCondition.MANUAL))
        use(SpawnpointModule(SpawnpointModule.SingleSpawnpointProvider(spawnPosition)))
        use(CustomGeneratorInstanceModule(
            CustomGeneratorInstanceModule.getFullbrightDimension()
        ) { /* void world - no generation to do */ })
        use(InstantRespawnModule())
        use(SpectatorModule(spectateOnDeath = true))
        use(CustomDeathMessageModule())
        use(object : GameModule() {
            var started = false
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(GameStartEvent::class.java) {
                    started = true
                    blocks.forEach { block ->
                        block.spawnTime = getInstance().worldAge // Reset age of the block
                    }
                }
                eventNode.addListener(PlayerDeathEvent::class.java) { event ->
                    if (state != GameState.INGAME) return@addListener

                    val score = blocks.count { it.isReached }

                    event.player.showTitle(
                        Title.title(
                            Component.text("GAME OVER!", NamedTextColor.RED, TextDecoration.BOLD),
                            Component.text("You scored $score points.", NamedTextColor.RED)
                        )
                    )
                    endGame(Duration.ofSeconds(10))
                }
                eventNode.addListener(InstanceTickEvent::class.java) {
                    if (state == GameState.INGAME) handleTick(it.instance.worldAge)
                }
                eventNode.addListener(PlayerMoveEvent::class.java) { event ->
                    if (!started) return@addListener
                    blocks.forEachIndexed { i, block ->
                        val pX = floor(event.player.position.x)
                        val pY = floor(event.player.position.y)
                        val pZ = floor(event.player.position.z)
                        if (!block.isReached && !block.isRemoved && event.isOnGround && pX == block.pos.x && pZ == block.pos.z && pY - block.pos.y <= 1.0) {
                            block.markReached(event.player)
                            MinecraftServer.getSchedulerManager().scheduleNextTick {
                                // Mark all blocks before this one as reached, just in case a block was skipped
                                blocks.forEachIndexed { j, previous ->
                                    if (j < i) {
                                        previous.markReached(event.player)
                                        MinecraftServer.getSchedulerManager().buildTask {
                                            previous.destroy()
                                        }.delay(Duration.ofSeconds(1)).schedule()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        })

        MinecraftServer.getSchedulerManager().scheduleNextTick {
            repeat(3) { addNewBlock() }
        }

        ready()
    }

    private val blocks = mutableListOf(ParkourBlock(this, getInstance(), 0L, spawnPosition.sub(0.0, 1.0, 0.0)))

    private fun handleTick(ticks: Long) {
        blocks.forEach { block ->
            if (block.isRemoved) return@forEach
            val ticksSinceSpawn = ticks - block.spawnTime
            if (ticksSinceSpawn > blockLiveTime) {
                block.destroy()
            } else {
                block.tick(ticksSinceSpawn.toInt())
            }
        }
    }

    fun addNewBlock() {
        val instance = getInstance()
        val lastPos = blocks.last().pos
        val nextPos = getNextBlockPosition()
        val block = ParkourBlock(this, instance, instance.worldAge, nextPos)
        blocks.add(block)
        if (nextPos.y - lastPos.y >= 2) {
            block.addLadders()
        }

    }

    private fun getNextBlockPosition(): Pos {
        val lastBlock = blocks.last()
        val lastPos = lastBlock.pos
        angle += Math.toRadians((-45..45).random().toDouble())
        val yDiff = (-2..2).random().toDouble()
        val vec = Vec(cos(angle), sin(angle)).mul(2.0 - yDiff + Random.nextDouble(1.0, 2.0)).withY(yDiff)
        if (vec.x < 1.0 && vec.z < 1.0) return getNextBlockPosition()
        return lastPos.add(vec)
    }

    var angle = 0.0

    class ParkourBlock(val game: InfinijumpGame, val instance: Instance, var spawnTime: Long, posIn: Pos) {

        private var hasLadders = false

        private val placedBlockType = Block.STONE_BRICKS
        private val fallingBlockType = Block.INFESTED_STONE_BRICKS
        val pos = Pos(posIn.blockX().toDouble(), posIn.blockY().toDouble(), posIn.blockZ().toDouble())

        private val centerBottom = Pos(
            pos.blockX().toDouble() + 0.5, pos.blockY().toDouble(), pos.blockZ().toDouble() + 0.5
        ) // In the center of the block on the X and Z axes
        private val center = centerBottom.add(0.0, 0.5, 0.0) // In the center of the block on all axes

        val entity = Entity(EntityType.FALLING_BLOCK)

        var isReached = false
        var isRemoved = false

        fun markReached(player: Player) {
            if (isReached) return
            isReached = true
            game.score++
            val packet = PacketUtils.createBlockParticle(player.position, Block.STONE_BRICKS, 10)
            val sound = Sound.sound(
                SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.BLOCK, 1.0f, 0.5f + game.score * 0.025f
            )
            player.sendPacket(packet)
            player.playSound(sound)
            player.showTitle(
                Title.title(
                    Component.empty(),
                    Component.text(game.score, NamedTextColor.GOLD),
                    Title.Times.times(Duration.ZERO, Duration.ZERO, Duration.ofSeconds(60))
                )
            )

            MinecraftServer.getSchedulerManager().scheduleNextTick { game.addNewBlock() }
        }

        fun tick(aliveTicks: Int) {
            val color = when {
                isReached -> NamedTextColor.WHITE
                aliveTicks < (blockLiveTime / 3) -> NamedTextColor.GREEN
                aliveTicks < (2 * blockLiveTime / 3) -> NamedTextColor.YELLOW
                else -> NamedTextColor.RED
            }
            val p = Pos(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble())
            if (aliveTicks % 10 == 0) instance.getChunkAt(pos)
                ?.sendPacketToViewers(BlockBreakAnimationPacket(Random.nextInt(), p, (aliveTicks / (blockLiveTime / 10)).toByte()))
            setOutlineColor(color)

            if (aliveTicks % 10 != 0) return
            val packet = PacketUtils.createParticleWithBlockState(
                pos.add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5),
                Particle.FALLING_DUST,
                Block.STONE_BRICKS,
                2
            )
            instance.sendGroupedPacket(packet)
        }

        private fun setOutlineColor(color: NamedTextColor) {
            GlowingEntityUtils.glow(entity, color, entity.viewers)
        }

        init {
            instance.setBlock(pos, placedBlockType)
            (entity.entityMeta as FallingBlockMeta).apply {
                this.isInvisible = false
                this.isHasNoGravity = true
                this.block = fallingBlockType
            }
            entity.setInstance(instance, centerBottom)
        }

        fun destroy() {
            if (isRemoved) return
            isRemoved = true
            instance.setBlock(pos, Block.AIR)
            entity.remove()

            val packet = PacketUtils.createParticlePacket(center, Particle.DRIPPING_LAVA, 5)
            val sound = Sound.sound(SoundEvent.BLOCK_STONE_BREAK, Sound.Source.BLOCK, 1.0f, 1.0f)
            instance.sendGroupedPacket(packet)
            SoundUtils.playSoundInWorld(sound, instance, center)

            if (hasLadders) {
                instance.setBlock(pos.sub(1.0, 0.0, 0.0), Block.AIR)
                instance.setBlock(pos.sub(0.0, 0.0, 1.0), Block.AIR)
                instance.setBlock(pos.add(1.0, 0.0, 0.0), Block.AIR)
                instance.setBlock(pos.add(0.0, 0.0, 1.0), Block.AIR)
            }
        }

        fun addLadders() {
            instance.setBlock(pos.sub(1.0, 0.0, 0.0), Block.LADDER.withProperty("facing", "west"))
            instance.setBlock(pos.sub(0.0, 0.0, 1.0), Block.LADDER.withProperty("facing", "north"))
            instance.setBlock(pos.add(1.0, 0.0, 0.0), Block.LADDER.withProperty("facing", "east"))
            instance.setBlock(pos.add(0.0, 0.0, 1.0), Block.LADDER.withProperty("facing", "south"))
            hasLadders = true
        }
    }
}