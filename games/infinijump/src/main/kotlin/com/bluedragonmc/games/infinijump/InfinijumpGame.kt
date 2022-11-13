package com.bluedragonmc.games.infinijump

import com.bluedragonmc.api.grpc.CommonTypes.GameType.GameTypeFieldSelector
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.games.infinijump.block.*
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.gameplay.InventoryPermissionsModule
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.PlayerResetModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.withColor
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.time.Duration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal const val blocksPerDifficulty = 50

class InfinijumpGame(mapName: String?) : Game("Infinijump", mapName ?: "Classic") {

    companion object {
        private val PLAY_AGAIN_ITEM_TAG = Tag.Boolean("is_play_again_item")
        private val LOBBY_ITEM_TAG = Tag.Boolean("is_lobby_item")
    }

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
        use(CustomDeathMessageModule())
        use(InventoryPermissionsModule(false, false))
        use(ConfigModule())
        use(CosmeticsModule())
        use(AwardsModule())

        blocks = mutableListOf(
            ParkourBlock(this, getInstance(), 0L, spawnPosition.sub(0.0, 1.0, 0.0)).apply { create(getNextBlockType()) }
        )

        handleEvent<PlayerSpawnEvent>(CosmeticsModule::class) { event ->
            event.player.showTitle(
                Title.title(
                    "Infinijump" withColor BRAND_COLOR_PRIMARY_1,
                    "Move to start" withColor BRAND_COLOR_PRIMARY_2,
                    Title.Times.times(Duration.ofMillis(500), Duration.ofDays(3), Duration.ZERO)
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

            event.player.isFlying = true
            event.player.teleport(event.player.position.withY { y -> y.coerceAtLeast(80.0) })
            event.player.setHeldItemSlot(4)

            event.player.inventory.setItemStack(2, ItemStack.builder(Material.PAPER)
                .displayName(Component.translatable("game.infinijump.play_again", BRAND_COLOR_PRIMARY_2).noItalic())
                .apply { setTag(PLAY_AGAIN_ITEM_TAG, true) }
                .build())

            event.player.inventory.setItemStack(4, ItemStack.builder(Material.RED_CONCRETE)
                .displayName(Component.translatable("game.infinijump.loss_score", NamedTextColor.RED, Component.text(score)).noItalic())
                .build())

            event.player.inventory.setItemStack(6, ItemStack.builder(Material.ARROW)
                .displayName(Component.translatable("game.infinijump.quit", BRAND_COLOR_PRIMARY_2).noItalic())
                .apply { setTag(LOBBY_ITEM_TAG, true) }
                .build())

            runBlocking {
                getModule<StatisticsModule>()
                    .recordStatisticIfGreater(event.player, "game_infinijump_highest_score", score.toDouble())
            }

            // If the player scores more than 20 points, award them 1 coin for every 4 points scored.
            if (score > 20) {
                getModule<AwardsModule>().awardCoins(
                    event.player, score / 4,
                    Component.translatable("game.infinijump.award.score")
                )
            }

            blocks.forEach { it.instance.setBlock(it.pos, it.placedBlockType) }
            state = GameState.ENDING

            // Automatically send the player to the lobby after 20 seconds
            MinecraftServer.getSchedulerManager().buildTask {
                if (getInstanceOrNull() != null) {
                    ArrayList(players).forEach { p ->
                        Environment.current.queue.queue(p, gameType {
                            name = "Lobby"
                            selectors += GameTypeFieldSelector.GAME_NAME
                        })
                    }
                }
            }.delay(Duration.ofSeconds(20)).repeat(Duration.ofSeconds(10)).schedule()

            var secondsLeft = 20
            MinecraftServer.getSchedulerManager().buildTask {
                players.forEach { p ->
                    p.sendActionBar(
                        Component.translatable("game.infinijump.returning_to_lobby", BRAND_COLOR_PRIMARY_2,
                            Component.text(secondsLeft, NamedTextColor.WHITE))
                    )
                }
                secondsLeft = (secondsLeft - 1).coerceAtLeast(0)
            }.repeat(Duration.ofSeconds(1)).schedule()
        }

        handleEvent<PlayerUseItemEvent> { event ->
            when {
                event.itemStack.hasTag(PLAY_AGAIN_ITEM_TAG) -> {
                    val game = InfinijumpGame(mapName)
                    game.addPlayer(event.player)
                }
                event.itemStack.hasTag(LOBBY_ITEM_TAG) -> {
                    Environment.current.queue.queue(event.player, gameType {
                        name = "Lobby"
                        selectors += GameTypeFieldSelector.GAME_NAME
                    })
                }
            }
        }

        handleEvent<InstanceTickEvent> { event ->
            if (state == GameState.INGAME) handleTick(event.instance.worldAge)
            if (blocks.size <= 3 && players.isNotEmpty()) addNewBlock() // Add a new block every tick until there are 3 blocks
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
            if (state != GameState.INGAME) return@handleEvent
            blocks.forEachIndexed { i, block ->
                if (!block.isReached && !block.isRemoved && event.isOnGround &&
                    block.placedBlockType.registry().collisionShape().intersectBox(
                        event.player.position.sub(block.pos).sub(0.0, 1.0, 0.0), event.player.boundingBox
                    )) {
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
        val blockType = getNextBlockType()
        val block = when {
            nextPos.y - lastPos.y <= -5 -> PlatformParkourBlock(this, instance, instance.worldAge, nextPos)
            nextPos.y - lastPos.y >= 2 -> LadderParkourBlock(this, instance, instance.worldAge, nextPos)
            Math.random() < 0.01 * difficulty -> IronBarParkourBlock(this, instance, instance.worldAge, nextPos)
            else -> HighlightedParkourBlock(this, instance, instance.worldAge, nextPos)
        }
        blocks.add(block)
        block.create(blockType)
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

    private fun getNextBlockType(): Block {
        if (players.isEmpty()) return Block.BEDROCK
        val player = players.first()
        val cosmetic = getModule<CosmeticsModule>().getCosmeticInGroup<InfinijumpCosmetic>(player)
        return cosmetic?.blockType?.invoke() ?: Block.STONE_BRICKS
    }

    private var angle = 180.0
}