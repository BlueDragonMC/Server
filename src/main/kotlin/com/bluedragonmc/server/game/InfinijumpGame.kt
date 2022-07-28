package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.GameState
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import com.bluedragonmc.server.module.gameplay.SpectatorModule
import com.bluedragonmc.server.module.gameplay.VoidDeathModule
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.packet.GlowingEntityUtils
import com.bluedragonmc.server.utils.packet.PacketUtils
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.minestom.server.utils.NamespaceID
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

class InfinijumpGame(mapName: String) : Game("Infinijump", mapName) {

    private val spawnPosition = Pos(0.0, 64.0, 0.0)

    override val maxPlayers = 1

    init {
        use(VoidDeathModule(0.0))
        use(CountdownModule(threshold = 1, allowMoveDuringCountdown = false, countdownSeconds = 5))
        use(WinModule(WinModule.WinCondition.MANUAL))
        use(SpawnpointModule(SpawnpointModule.SingleSpawnpointProvider(spawnPosition)))
        use(CustomGeneratorInstanceModule(
            MinecraftServer.getDimensionTypeManager().getDimension(
                NamespaceID.from("bluedragon:fullbright_dimension")
            )!!
        ) { /* void world - no generation to do */ })
        use(InstantRespawnModule())
        use(SpectatorModule(spectateOnDeath = true))
        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(GameStartEvent::class.java) {
                    blocks.first().spawnTime = getInstance().worldAge + 20 // Reset age of first block
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
                    blocks.forEach { block ->
                        if (!block.isReached && !block.isRemoved && block.pos.distanceSquared(event.player.position) < 3.0) {
                            block.isReached = true
                            val packet =
                                PacketUtils.createBlockParticle(block.pos.add(0.0, 1.0, 0.0), Block.SNOW_BLOCK, 10)
                            val sound =
                                Sound.sound(SoundEvent.BLOCK_AMETHYST_BLOCK_STEP, Sound.Source.BLOCK, 1.0f, 1.0f)
                            event.player.sendPacket(packet)
                            event.player.playSound(sound)
                        }
                    }
                }
            }
        })

        ready()
    }

    private val blocks = mutableListOf(ParkourBlock(getInstance(), 0L, spawnPosition.sub(0.0, 1.0, 0.0)))

    private fun handleTick(ticks: Long) {
        blocks.forEach { block ->
            if (block.isRemoved) return@forEach
            val ticksSinceSpawn = ticks - block.spawnTime
            if (ticksSinceSpawn > 100) {
                block.destroy()
                val packet =
                    PacketUtils.createParticleWithBlockState(block.pos, Particle.BLOCK_MARKER, Block.BARRIER, 1)
                block.instance.sendGroupedPacket(packet)
            } else {
                block.updateOutlineColor(ticksSinceSpawn.toInt())
            }
            val packet = PacketUtils.createParticleWithBlockState(block.pos, Particle.FALLING_DUST, Block.SNOW_BLOCK, 1)
            block.instance.sendGroupedPacket(packet)
        }
        if (ticks % 30 == 0L) { // every 1.5 seconds, place a new block.
            val lastPos = blocks.lastOrNull()?.pos ?: spawnPosition
            val newPos = getNextBlockPosition(blocks.last(), lastPos, 3.0)

            blocks.add(ParkourBlock(getInstance(), ticks, newPos))
        }
    }

    private fun getNextBlockPosition(lastBlock: ParkourBlock, pos: Pos, delta: Double): Pos {
        val newPosition = pos.add(
            randomPlusOrMinus(delta), randomPlusOrMinus(1.0), randomPlusOrMinus(delta)
        )
        if (newPosition.distanceSquared(lastBlock.pos) <= 3.0) return getNextBlockPosition(
            lastBlock, newPosition, delta
        )
        return newPosition
    }

    private fun randomPlusOrMinus(delta: Double) = Random.nextDouble(delta * 2.0) - delta

    class ParkourBlock(val instance: Instance, var spawnTime: Long, initialPos: Pos) {

        private val block = Block.SNOW_BLOCK
        val pos = Pos(initialPos.blockX().toDouble(), initialPos.blockY().toDouble(), initialPos.blockZ().toDouble())
        val entity = FloatingBlockEntity(block)

        var isReached = false
        var isRemoved = false

        fun updateOutlineColor(aliveTicks: Int) {
            val color = when {
                aliveTicks < 20 -> NamedTextColor.GREEN
                aliveTicks < 50 -> NamedTextColor.YELLOW
                else -> NamedTextColor.RED
            }
            setOutlineColor(color)
        }

        private fun setOutlineColor(color: NamedTextColor) {
            GlowingEntityUtils.glow(entity, color)
        }

        init {
            instance.setBlock(pos, Block.BARRIER)
            entity.setInstance(instance, pos)
        }

        fun destroy() {
            instance.setBlock(pos, Block.AIR)
            isRemoved = true
            GlowingEntityUtils.cleanup(entity)
            entity.remove()
        }
    }

    class FloatingBlockEntity(block: Block) : Entity(EntityType.FALLING_BLOCK) {

        init {
            (entityMeta as FallingBlockMeta).apply {
                this.isInvisible = false
                this.isHasNoGravity = true
                this.block = block
            }
        }

        override fun setInstance(instance: Instance, spawnPosition: Pos): CompletableFuture<Void> {
            return super.setInstance(instance, spawnPosition.add(0.5, 0.0, 0.5))
        }
    }
}