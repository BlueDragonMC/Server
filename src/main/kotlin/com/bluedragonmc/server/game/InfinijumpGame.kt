package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.GameState
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.gameplay.PlayerResetModule
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import com.bluedragonmc.server.module.gameplay.SpectatorModule
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.utils.SoundUtils
import com.bluedragonmc.server.utils.packet.GlowingEntityUtils
import com.bluedragonmc.server.utils.packet.PacketUtils
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val blocksPerDifficulty = 50

class InfinijumpGame(mapName: String?) : Game("Infinijump", mapName ?: "Classic") {

    private val blockLiveTime = 120
        get() = field - difficulty * 15

    private var score = 0
        set(value) {
            field = value
            if (field % blocksPerDifficulty == 0) difficulty++
            players.forEach { it.exp = (value.toFloat() % blocksPerDifficulty) / blocksPerDifficulty }
        }
    private var difficulty = 0
        set(value) {
            lateinit var title: String
            lateinit var color: TextColor
            when (value) {
                1 -> {
                    title = "Medium"
                    color = NamedTextColor.YELLOW
                }
                2 -> {
                    title = "Hard"
                    color = NamedTextColor.RED
                }
                3 -> {
                    title = "Very Hard"
                    color = NamedTextColor.DARK_RED
                }
                else -> return
            }
            field = value
            playSound(Sound.sound(SoundEvent.ENTITY_ENDER_DRAGON_GROWL, Sound.Source.PLAYER, 1.0f, 1.0f))
            players.forEach {
                it.sendMessage(Component.translatable("game.infinijump.difficulty_increased", color, Component.text(title)))
                it.level = value
            }
        }
    override val maxPlayers = 1

    private val spawnPosition = Pos(0.5, 64.0, 0.5)

    init {
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(CountdownModule(threshold = 1, allowMoveDuringCountdown = false, countdownSeconds = 3))
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
                    blocks.forEachIndexed { i, block ->
                        block.spawnTime = getInstance().worldAge + 40 * i // Reset age of the block
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
                    endGame(Duration.ofSeconds(3))
                }
                eventNode.addListener(InstanceTickEvent::class.java) {
                    if (state == GameState.INGAME) handleTick(it.instance.worldAge)
                    if (blocks.size <= 3) addNewBlock() // Add a new block every tick until there are 3 blocks
                }
                eventNode.addListener(PlayerMoveEvent::class.java) { event ->
                    if (!started) return@addListener
                    blocks.forEachIndexed { i, block ->
                        if (!block.isReached && !block.isRemoved && event.isOnGround && event.player.boundingBox.intersectEntity(
                                event.player.position.sub(0.0, 1.0, 0.0),
                                block.entity)
                        ) {
                            block.markReached(event.player)
                            MinecraftServer.getSchedulerManager().scheduleNextTick {
                                // Mark all blocks before this one as reached, just in case a block was skipped
                                blocks.forEachIndexed { j, previous ->
                                    if (j < i) {
                                        previous.markReached(event.player)
                                        previous.destroy()
                                    }
                                }
                            }
                        }
                    }
                    if (event.newPosition.y < blocks.minOf { it.pos.blockY() }) {
                        event.player.damage(DamageType.VOID, Float.MAX_VALUE)
                    }
                }
            }
        })

        ready()
    }

    private val blocks = mutableListOf(
        ParkourBlock(this, getInstance(), 0L, spawnPosition.sub(0.0, 1.0, 0.0)).apply { create() }
    )

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
        val block = when {
            nextPos.y - lastPos.y <= -5 -> PlatformParkourBlock(this, instance, instance.worldAge, nextPos)
            nextPos.y - lastPos.y >= 2 -> LadderParkourBlock(this, instance, instance.worldAge, nextPos)
            Math.random() < 0.01 * difficulty -> IronBarParkourBlock(this, instance, instance.worldAge, nextPos)
            else -> ParkourBlock(this, instance, instance.worldAge, nextPos)
        }
        blocks.add(block)
        block.create()
    }

    private fun getNextBlockPosition(): Pos {
        val lastBlock = blocks.last()
        val lastPos = lastBlock.pos
        angle += Math.toRadians((-35..35).random().toDouble())
        val yDiff = Random.nextDouble(-1.0, 2.0)
        if (lastPos.y + yDiff < 1.0) return getNextBlockPosition()
        val vec = Vec(cos(angle), sin(angle)).mul(1.5 + (difficulty / 4) - (yDiff / 2) + Random.nextDouble(1.0, 2.5))
            .withY(yDiff.roundToInt().toDouble())
        if (Math.random() < 0.2 && lastPos.y > 80) {
            return lastPos.add(vec).sub(0.0, abs(yDiff) * 8.0, 0.0)
        }
        if (vec.x < 1.0 && vec.z < 1.0) return getNextBlockPosition()
        return lastPos.add(vec)
    }

    var angle = 0.0

    open class ParkourBlock(val game: InfinijumpGame, val instance: Instance, var spawnTime: Long, posIn: Pos) {

        /**
         * Setting the placed block type will determine whether the breaking animation is visible.
         * Placed barriers have no breaking animation.
         */
        protected open val placedBlockType: Block = Block.STONE_BRICKS

        /**
         *
         * Setting the falling block type will determine the shape of the block's glow effect.
         * Barriers and invisible falling blocks have no glowing effect.
         */
        protected open val fallingBlockType: Block? = Block.INFESTED_STONE_BRICKS

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
            val packet = PacketUtils.createBlockParticle(player.position, placedBlockType, 10)
            val sound = Sound.sound(
                when (game.difficulty) {
                    0 -> SoundEvent.BLOCK_NOTE_BLOCK_PLING
                    1 -> SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP
                    2 -> SoundEvent.BLOCK_AMETHYST_BLOCK_BREAK
                    3 -> SoundEvent.BLOCK_BASALT_BREAK
                    else -> SoundEvent.BLOCK_NOTE_BLOCK_PLING
                },
                Sound.Source.BLOCK,
                1.0f,
                if (game.score >= 4 * blocksPerDifficulty) 2.0f else 0.5f + (game.score % blocksPerDifficulty) * (1.5f / blocksPerDifficulty) // Max pitch = 2
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

        open fun tick(aliveTicks: Int) {
            val color = when {
                isReached -> NamedTextColor.WHITE
                aliveTicks < (game.blockLiveTime / 3) -> NamedTextColor.GREEN
                aliveTicks < (2 * game.blockLiveTime / 3) -> NamedTextColor.YELLOW
                else -> NamedTextColor.RED
            }
            val p = Pos(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble())
            if (aliveTicks % 10 == 0) {
                sendBlockAnimation(p, (aliveTicks / (game.blockLiveTime / 10)).toByte())
            }
            setOutlineColor(color)

            if (aliveTicks % 10 != 0) return
            val packet = PacketUtils.createParticleWithBlockState(
                pos.add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5),
                Particle.FALLING_DUST,
                placedBlockType,
                2
            )
            instance.sendGroupedPacket(packet)
        }

        private fun setOutlineColor(color: NamedTextColor) {
            GlowingEntityUtils.glow(entity, color, entity.viewers)
        }

        protected fun sendBlockAnimation(pos: Pos, progress: Byte) {
            instance.getChunkAt(pos)?.sendPacketToViewers(BlockBreakAnimationPacket(Random.nextInt(), pos, progress))
        }

        protected fun setNeighboringBlocks(block: Block, corners: Boolean = false) =
            forEachNeighboringBlock(corners = corners) { pos ->
                instance.setBlock(pos, block)
            }

        protected inline fun forEachNeighboringBlock(corners: Boolean = false, block: (Pos) -> Unit) {
            block(pos.add(1.0, 0.0, 0.0))
            block(pos.add(0.0, 0.0, 1.0))
            block(pos.sub(1.0, 0.0, 0.0))
            block(pos.sub(0.0, 0.0, 1.0))

            if (corners) {
                block(pos.add(1.0, 0.0, 1.0))
                block(pos.add(1.0, 0.0, -1.0))
                block(pos.add(-1.0, 0.0, 1.0))
                block(pos.add(-1.0, 0.0, -1.0))
            }
        }

        open fun create() {
            instance.setBlock(pos, placedBlockType)
            if (fallingBlockType != null)
                (entity.entityMeta as FallingBlockMeta).apply {
                    this.isInvisible = false
                    this.isHasNoGravity = true
                    this.block = fallingBlockType!!
                }
            entity.setInstance(instance, centerBottom)
        }

        open fun destroy() {
            if (isRemoved) return
            isRemoved = true
            instance.setBlock(pos, Block.AIR)
            entity.remove()

            val packet = PacketUtils.createParticlePacket(center, Particle.DRIPPING_LAVA, 5)
            val sound = Sound.sound(SoundEvent.BLOCK_STONE_BREAK, Sound.Source.BLOCK, 1.0f, 1.0f)
            instance.sendGroupedPacket(packet)
            SoundUtils.playSoundInWorld(sound, instance, center)
        }
    }

    class LadderParkourBlock(game: InfinijumpGame, instance: Instance, spawnTime: Long, posIn: Pos) :
        ParkourBlock(game, instance, spawnTime, posIn) {

        init {
            instance.setBlock(pos.sub(1.0, 0.0, 0.0), Block.LADDER.withProperty("facing", "west"))
            instance.setBlock(pos.sub(0.0, 0.0, 1.0), Block.LADDER.withProperty("facing", "north"))
            instance.setBlock(pos.add(1.0, 0.0, 0.0), Block.LADDER.withProperty("facing", "east"))
            instance.setBlock(pos.add(0.0, 0.0, 1.0), Block.LADDER.withProperty("facing", "south"))
        }

        override fun destroy() {
            super.destroy()

            setNeighboringBlocks(Block.AIR)
        }
    }

    class IronBarParkourBlock(game: InfinijumpGame, instance: Instance, spawnTime: Long, posIn: Pos) :
        ParkourBlock(game, instance, spawnTime, posIn) {
        override val placedBlockType: Block = Block.IRON_BARS
        override val fallingBlockType: Block = Block.BARRIER
    }

    class PlatformParkourBlock(game: InfinijumpGame, instance: Instance, spawnTime: Long, posIn: Pos) :
        ParkourBlock(game, instance, spawnTime, posIn) {
        override val placedBlockType: Block = Block.RED_CONCRETE
        override val fallingBlockType: Block? = Block.BARRIER

        override fun create() {
            super.create()
            setNeighboringBlocks(Block.RED_CONCRETE, corners = true)
        }

        override fun tick(aliveTicks: Int) {
            super.tick(aliveTicks)
            if (aliveTicks % 10 == 0) {
                forEachNeighboringBlock(corners = true) { pos ->
                    sendBlockAnimation(pos, (aliveTicks / (game.blockLiveTime / 10)).toByte())
                }
            }
        }

        override fun destroy() {
            super.destroy()
            setNeighboringBlocks(Block.AIR, corners = true)
        }
    }
}