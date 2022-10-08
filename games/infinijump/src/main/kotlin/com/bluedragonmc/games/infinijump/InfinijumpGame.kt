package com.bluedragonmc.games.infinijump

import com.bluedragonmc.games.infinijump.block.IronBarParkourBlock
import com.bluedragonmc.games.infinijump.block.LadderParkourBlock
import com.bluedragonmc.games.infinijump.block.ParkourBlock
import com.bluedragonmc.games.infinijump.block.PlatformParkourBlock
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.PlayerResetModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.minigame.SpectatorModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal const val blocksPerDifficulty = 50

class InfinijumpGame(mapName: String?) : Game("Infinijump", mapName ?: "Classic") {

    internal val blockLiveTime = 120
        get() = field - difficulty * 15

    internal var score = 0
        set(value) {
            field = value
            if (field % blocksPerDifficulty == 0) difficulty++
            players.forEach { it.exp = (value.toFloat() % blocksPerDifficulty) / blocksPerDifficulty }
        }

    internal var difficulty = 0
        set(value) {
            lateinit var title: String
            lateinit var color: TextColor
            when (value) {
                1 -> {
                    title = "game.infinijump.difficulty.medium"
                    color = NamedTextColor.YELLOW
                }
                2 -> {
                    title = "game.infinijump.difficulty.hard"
                    color = NamedTextColor.RED
                }
                3 -> {
                    title = "game.infinijump.difficulty.very_hard"
                    color = NamedTextColor.DARK_RED
                }
                else -> return
            }
            field = value
            playSound(Sound.sound(SoundEvent.ENTITY_ENDER_DRAGON_GROWL, Sound.Source.PLAYER, 1.0f, 1.0f))
            players.forEach {
                it.sendMessage(
                    Component.translatable(
                        "game.infinijump.difficulty_increased",
                        color,
                        Component.translatable(title)
                    )
                )
                it.level = value
            }
        }
    override val maxPlayers = 1

    private val spawnPosition = Pos(0.5, 64.0, 0.5)
    private val blocks: MutableList<ParkourBlock>

    private var started = false
    private var playerSpawnTime = 0L

    init {
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SpawnpointModule(SpawnpointModule.SingleSpawnpointProvider(spawnPosition)))
        use(CustomGeneratorInstanceModule(
            CustomGeneratorInstanceModule.getFullbrightDimension()
        ) { /* void world - no generation to do */ })
        use(InstantRespawnModule())
        use(SpectatorModule(spectateOnDeath = true))
        use(CustomDeathMessageModule())

        blocks = mutableListOf(
            ParkourBlock(this, getInstance(), 0L, spawnPosition.sub(0.0, 1.0, 0.0)).apply { create() }
        )

        handleEvent<PlayerSpawnEvent> { event ->
            event.player.showTitle(
                Title.title(
                    "Infinijump" withColor BRAND_COLOR_PRIMARY_1,
                    "Move to start" withColor BRAND_COLOR_PRIMARY_2
                )
            )
            playerSpawnTime = getInstance().worldAge
        }

        handleEvent<PlayerDeathEvent> { event ->
            if (state != GameState.INGAME) return@handleEvent

            val score = blocks.count { it.isReached }

            event.player.showTitle(
                Title.title(
                    Component.translatable("module.win.title.lost", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.translatable("game.infinijump.loss_score",
                        NamedTextColor.RED,
                        Component.text(score))
                )
            )
            endGame(Duration.ofMillis(((3 + score / 50f) * 1000).toLong()))
            getModule<StatisticsModule>().recordStatistic(event.player, "game_infinijump_highest_score", score.toDouble()) { prev ->
                prev == null || prev < score
            }

            blocks.forEach { it.instance.setBlock(it.pos, it.placedBlockType) }
        }

        handleEvent<InstanceTickEvent> { event ->
            if (state == GameState.INGAME) handleTick(event.instance.worldAge)
            if (blocks.size <= 3) addNewBlock() // Add a new block every tick until there are 3 blocks
        }

        handleEvent<PlayerMoveEvent> { event ->
            if (!started) {
                // If the game hasn't started and the player moved their position (not just their yaw and pitch)
                val old = event.player.position
                val new = event.newPosition
                if (old.x != new.x || old.z != new.z) {
                    callEvent(GameStartEvent(this))
                }
                return@handleEvent
            }
            blocks.forEachIndexed { i, block ->
                if (!block.isReached && !block.isRemoved && event.isOnGround && event.player.boundingBox.intersectEntity(
                        event.player.position.sub(0.0, 1.0, 0.0), block.entity
                    )
                ) {
                    // Mark all blocks before this one as reached, just in case a block was skipped
                    blocks.forEachIndexed { j, previous ->
                        if (j < i) {
                            previous.markReached(event.player, show = false)
                            previous.destroy()
                        }
                    }
                    block.markReached(event.player)
                }
            }
            val nonRemovedBlocks = blocks.filter { !it.isRemoved }
            if (nonRemovedBlocks.isEmpty() || event.newPosition.y < nonRemovedBlocks.minOf { it.pos.blockY() }) {
                event.player.damage(DamageType.VOID, Float.MAX_VALUE)
            }
        }

        onGameStart {
            started = true
            blocks.forEachIndexed { i, block ->
                block.spawnTime = getInstance().worldAge + 40 * i // Reset age of the block
            }
            state = GameState.INGAME
        }

        use(StatisticsModule(recordWins = false))

        getInstanceOrNull()?.apply {
            time = 18000
            timeRate = 0
        }

        ready()
    }

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
        val vec = Vec(cos(angle), -sin(angle)).mul(1.75 + (difficulty / 4) - (yDiff / 2) + Random.nextDouble(1.0, 2.5))
            .withY(yDiff.roundToInt().toDouble())
        if (Math.random() < 0.2 && lastPos.y > 80) {
            return lastPos.add(vec).sub(0.0, abs(yDiff) * 8.0, 0.0)
        }
        if (vec.x < 1.0 && vec.z < 1.0) return getNextBlockPosition()
        return lastPos.add(vec)
    }

    var angle = 180.0
}