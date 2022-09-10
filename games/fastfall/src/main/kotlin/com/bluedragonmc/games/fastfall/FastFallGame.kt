package com.bluedragonmc.games.fastfall

import com.bluedragonmc.server.*
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.gameplay.MaxHealthModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.gameplay.WorldPermissionsModule
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.FallDamageModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.withTransition
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.message.Messenger.sendMessage
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.properties.Delegates

class FastFallGame(mapName: String?) : Game("FastFall", mapName ?: "Chaos") {
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
        var isSingleplayer by Delegates.notNull<Boolean>()
        var startTime: Long? = null

        use(object : GameModule() {

            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(GameStartEvent::class.java) {
                    startTime = System.currentTimeMillis()
                    isSingleplayer = parent.players.size <= 1

                    if (isSingleplayer) parent.players.forEach {
                        it.sendMessage(Component.translatable("game.fastfall.singleplayer_warning", ALT_COLOR_1))
                    }
                }
                eventNode.addListener(WinModule.WinnerDeclaredEvent::class.java) { event ->
                    event.winningTeam.players.forEach {
                        if (it.health == it.maxHealth) {
                            val amount = if (isSingleplayer) 10 else 50
                            parent.getModule<AwardsModule>().awardCoins(
                                it, amount, Component.translatable("game.fastfall.award.no_damage_taken", ALT_COLOR_2)
                            )
                        }
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
                eventNode.addListener(PlayerMoveEvent::class.java) { event ->
                    if (
                        event.player.instance?.getBlock(event.player.position.sub(0.0, 0.2, 0.0)) == Block.EMERALD_BLOCK &&
                        event.player.isOnGround
                    ) {
                        getModule<WinModule>().declareWinner(event.player)
                    }
                }
                eventNode.addListener(WinModule.WinnerDeclaredEvent::class.java) { event ->
                    if (event.winningTeam.players.isEmpty()) return@addListener // The game was ended before a winner was declared, or there was no winner
                    val time = System.currentTimeMillis() - startTime!!
                    val player = event.winningTeam.players.first()
                    // Record the players' best times (only update the statistic if the new value is less than the old value)
                    getModule<StatisticsModule>().recordStatistic(player, "game_fastfall_best_time", time.toDouble()) { prev ->
                        prev == null || prev > time
                    }
                    if (!isSingleplayer) {
                        // Only record wins in multiplayer
                        getModule<StatisticsModule>().recordStatistic(player, "game_fastfall_wins")
                        { prev -> prev?.plus(1.0) ?: 1.0 }
                    }
                }
            }
        })

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

        ready()
    }
}