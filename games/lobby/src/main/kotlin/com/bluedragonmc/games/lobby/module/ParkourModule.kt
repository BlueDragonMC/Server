package com.bluedragonmc.games.lobby.module

import com.bluedragonmc.server.*
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.DoubleJumpModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.utils.formatDuration
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.hologram.Hologram
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerRespawnEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.utils.NamespaceID
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.objectmapping.ConfigSerializable

class ParkourModule(private val config: ConfigurationNode) : GameModule() {

    override val dependencies = listOf(InstanceModule::class)

    @ConfigSerializable
    data class ParkourCourse(val id: String, val start: Pos, val end: Pos, val name: Component, val checkpoints: List<Pos>) {
        // Empty constructor for Configurate to use while object mapping
        constructor() : this("unknown", Pos.ZERO, Pos.ZERO, Component.empty(), emptyList())
    }

    private lateinit var parent: Game
    private lateinit var courses: List<ParkourCourse>
    private lateinit var coursesByName: Map<String, ParkourCourse>

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        // Load configuration
        courses = config.getList(ParkourCourse::class.java) ?: emptyList()
        coursesByName = courses.associateBy { it.id }
        // Place pressure plates
        val instance = parent.getInstance()
        courses.forEach { course ->
            // Parkour start
            instance.setBlock(course.start, Block.HEAVY_WEIGHTED_PRESSURE_PLATE.withHandler(PressurePlateHandler { player ->
                // Starting pressure plate
                if (getCourse(player) == null) {
                    player.setTag(PARKOUR_START_TAG, System.currentTimeMillis())
                    setCourse(player, course)
                    player.sendMessage(Component.translatable("lobby.parkour.started", BRAND_COLOR_PRIMARY_1))
                    player.playSound(Sound.sound(SoundEvent.BLOCK_BEACON_ACTIVATE, Sound.Source.BLOCK, 1.0f, 1.0f))
                    // Prevent the player from double jumping during the parkour
                    DoubleJumpModule.blockDoubleJump(player, "parkour")
                }
            }))
            Hologram(instance, course.start.add(0.5, 1.5, 0.5), course.name)
            Hologram(instance, course.start.add(0.5, 1.2, 0.5), Component.translatable("lobby.parkour.start", ALT_COLOR_1))
            // Parkour end
            instance.setBlock(course.end, Block.HEAVY_WEIGHTED_PRESSURE_PLATE.withHandler(PressurePlateHandler { player ->
                if (getCourse(player) == course) {
                    val currentCheckpoint = player.getTag(CURRENT_CHECKPOINT_TAG)
                    val skippedCheckpoints = (course.checkpoints.isNotEmpty() &&
                            course.checkpoints.size != currentCheckpoint)
                    if (skippedCheckpoints) {
                        player.sendMessage(Component.translatable("lobby.parkour.checkpoint_skipped", NamedTextColor.RED))
                        if (currentCheckpoint != null && currentCheckpoint > 0) {
                            player.teleport(course.checkpoints[currentCheckpoint - 1])
                        } else player.teleport(course.start)
                        return@PressurePlateHandler
                    }
                    val time = System.currentTimeMillis() - player.getTag(PARKOUR_START_TAG)!!

                    player.removeTag(CURRENT_COURSE_TAG)
                    player.removeTag(CURRENT_CHECKPOINT_TAG)
                    player.removeTag(PARKOUR_START_TAG)

                    player.sendMessage(Component.translatable("lobby.parkour.completed", BRAND_COLOR_PRIMARY_2,
                        Component.text(formatDuration(time))))
                    player.playSound(Sound.sound(SoundEvent.BLOCK_BEACON_DEACTIVATE, Sound.Source.BLOCK, 1.0f, 1.0f))
                    if (parent.hasModule<DoubleJumpModule>()) player.isAllowFlying = true
                    parent.getModuleOrNull<StatisticsModule>()?.apply {
                        recordStatistic(player, "lobby_parkour_${course.id}_best_time", time.toDouble()) { old ->
                            if (old == null || old > time) {
                                player.sendMessage(Component.translatable("lobby.parkour.new_best", ALT_COLOR_1,
                                    setOf(TextDecoration.BOLD), Component.text(formatDuration(time), ALT_COLOR_2)))
                                true
                            } else false
                        }
                    }
                }
            }))
            Hologram(instance, course.end.add(0.5, 1.5, 0.5), course.name)
            Hologram(instance, course.end.add(0.5, 1.2, 0.5), Component.translatable("lobby.parkour.finish", ALT_COLOR_1))
            // Parkour checkpoints
            course.checkpoints.forEachIndexed { i, checkpoint ->
                instance.setBlock(checkpoint, Block.LIGHT_WEIGHTED_PRESSURE_PLATE.withHandler(PressurePlateHandler { player ->
                    if (getCourse(player) == course) {
                        val currentCheckpoint = player.getTag(CURRENT_CHECKPOINT_TAG)
                        if (currentCheckpoint != null && currentCheckpoint > i) return@PressurePlateHandler // The player already visited this checkpoint
                        val skipped = (currentCheckpoint != null && currentCheckpoint != i) || (currentCheckpoint == null && i != 0)
                        if (skipped) {
                            // The player skipped a checkpoint!
                            if (currentCheckpoint > 0) {
                                player.teleport(course.checkpoints[currentCheckpoint - 1])
                            } else {
                                player.teleport(course.start)
                            }
                            player.sendMessage(Component.translatable("lobby.parkour.checkpoint_skipped", NamedTextColor.RED))
                        } else {
                            // Update the player's checkpoint
                            player.sendMessage(Component.translatable("lobby.parkour.checkpoint_reached", BRAND_COLOR_PRIMARY_3, Component.text(i + 1)))
                            player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.BLOCK, 1.0f, 1.0f))
                            player.setTag(CURRENT_CHECKPOINT_TAG, i + 1)
                        }
                    }
                }))
                Hologram(instance, checkpoint.add(0.5, 1.5, 0.5), course.name)
                Hologram(instance, checkpoint.add(0.5, 1.2, 0.5), Component.translatable("lobby.parkour.checkpoint", ALT_COLOR_1, Component.text(i + 1)))
            }
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val block = event.instance.getBlock(event.player.position)
            if (block.compare(Block.HEAVY_WEIGHTED_PRESSURE_PLATE) || block.compare(Block.LIGHT_WEIGHTED_PRESSURE_PLATE)) {
                block.handler()?.onTouch(BlockHandler.Touch(block, event.instance, event.newPosition, event.player))
            }
        }

        eventNode.addListener(PlayerDeathEvent::class.java, ::cancelParkour)
        eventNode.addListener(PlayerRespawnEvent::class.java, ::cancelParkour)
        eventNode.addListener(PlayerLeaveGameEvent::class.java, ::cancelParkour)

        val inParkourNode = EventNode.event("in-parkour", EventFilter.PLAYER) { event ->
            event.player.hasTag(PARKOUR_START_TAG) && event.player.hasTag(CURRENT_COURSE_TAG)
        }
        eventNode.addChild(inParkourNode)

        inParkourNode.addListener(PlayerTickEvent::class.java) { event ->
            val course = getCourse(event.player)
            val time = System.currentTimeMillis() - event.player.getTag(PARKOUR_START_TAG)
            event.player.sendActionBar(Component.translatable("lobby.parkour.actionbar", BRAND_COLOR_PRIMARY_2,
                course?.name ?: Component.text("Parkour", NamedTextColor.YELLOW),
                Component.text(formatDuration(time), BRAND_COLOR_PRIMARY_1))
            )
        }

        inParkourNode.addListener(DoubleJumpModule.PlayerDoubleJumpEvent::class.java) { event ->
            event.isCancelled = true
            DoubleJumpModule.blockDoubleJump(event.player, "parkour")
        }
    }

    private fun cancelParkour(event: PlayerEvent) {
        val player = event.player
        if (getCourse(player) != null) {
            player.sendMessage(Component.translatable("lobby.parkour.cancelled", NamedTextColor.RED))
        }
        player.removeTag(CURRENT_COURSE_TAG)
        player.removeTag(CURRENT_CHECKPOINT_TAG)
        player.removeTag(PARKOUR_START_TAG)
        if (parent.hasModule<DoubleJumpModule>())
            DoubleJumpModule.unblockDoubleJump(event.player, "parkour", true)
    }

    private fun getCourse(player: Player) = player.getTag(CURRENT_COURSE_TAG)?.let { coursesByName[it] }
    private fun setCourse(player: Player, course: ParkourCourse) = player.setTag(CURRENT_COURSE_TAG, course.id)

    class PressurePlateHandler(private val onStep: (Player) -> Unit) : BlockHandler {
        override fun getNamespaceId() = NamespaceID.from("minecraft:heavy_weighted_pressure_plate")
        override fun onTouch(touch: BlockHandler.Touch) {
            onStep(touch.touching as? Player ?: return)
        }
    }

    companion object {
        private val CURRENT_COURSE_TAG = Tag.String("current_parkour_course")
        private val CURRENT_CHECKPOINT_TAG = Tag.Integer("current_parkour_checkpoint")
        private val PARKOUR_START_TAG = Tag.Long("parkour_start_timestamp")
    }
}