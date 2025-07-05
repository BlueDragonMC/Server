package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.ChestOpenEvent
import com.bluedragonmc.server.event.ChestPopulateEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.SoundUtils
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.InventoryType
import net.minestom.server.sound.SoundEvent
import java.awt.Menu

/**
 * Assigns a [Menu] to every chest in the world and allows them to be accessed by interacting with the chest.
 * Additionally, assigns an ender chest to every player, which can be accessed using any ender chest block.
 * Combine with [com.bluedragonmc.server.module.gameplay.ChestLootModule] to add auto-generated loot to chests.
 */
@DependsOn(GuiModule::class)
class ChestModule : GameModule() {

    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.player.gameMode == GameMode.SPECTATOR) return@addListener
            val chest: ChestBlock
            val inventoryType: InventoryType
            val pos: Point
            if (event.block.compare(Block.CHEST)) {
                val rootChest = getRootChest(event.instance, event.blockPosition)
                inventoryType = rootChest.first
                pos = rootChest.second

                chest = chests.getOrPut(pos) {
                    Chest(parent, inventoryType, event.instance, pos).also {
                        MinecraftServer.getGlobalEventHandler()
                            .call(ChestPopulateEvent(event.player, pos, pos, inventoryType, it.getMenu(event.player)))
                    }
                }

            } else if (event.block.compare(Block.ENDER_CHEST)) {
                chest = chests.getOrPut(event.blockPosition) {
                    EnderChest(parent,
                        event.blockPosition,
                        event.instance)
                }
                inventoryType = InventoryType.CHEST_3_ROW
                pos = event.blockPosition
            } else return@addListener

            MinecraftServer.getGlobalEventHandler().callCancellable(
                ChestOpenEvent(event.player, event.blockPosition, pos, inventoryType, chest.getMenu(event.player))
            ) {
                chest.open(event.player)
            }
            event.isBlockingItemUse = true
        }
    }

    val chests = mutableMapOf<Point, ChestBlock>()

    abstract class ChestBlock {

        private var isOpen: Boolean = false

        abstract fun getMenu(player: Player): GuiModule.Menu
        abstract val viewers: MutableSet<Player>
        abstract val instance: Instance
        abstract val position: Point

        abstract val openSound: Sound
        abstract val closeSound: Sound

        fun open(player: Player) {
            viewers.add(player)
            getMenu(player).open(player)
            if (!isOpen) {
                isOpen = true
                SoundUtils.playSoundInWorld(openSound, instance, position)
            }
            updateState()
        }

        fun onClosed(player: Player) {
            viewers.remove(player)
            if (viewers.isEmpty()) {
                isOpen = false
                SoundUtils.playSoundInWorld(closeSound, instance, position)
                updateState()
            }
        }

        private fun updateState() {
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                instance.sendBlockAction(position, 0x1, viewers.size.toByte())
            }
        }

//        init {
//            // This task mimicks the vanilla server's behavior, even though it is inefficient
//            MinecraftServer.getSchedulerManager().buildTask {
//                if (viewers.isNotEmpty()) instance.sendBlockAction(position, 0x1, viewers.size.toByte())
//            }.repeat(Duration.ofMillis(500)).schedule()
//        }
    }

    class EnderChest(private val game: Game, override val position: Point, override val instance: Instance) :
        ChestBlock() {

        companion object {
            private val enderChestMenus = mutableMapOf<Player, GuiModule.Menu>()
        }

        override fun getMenu(player: Player): GuiModule.Menu {
            return enderChestMenus.getOrPut(player) {
                val menu =
                    game.getModule<GuiModule>().createMenu(Component.translatable("container.enderchest"), InventoryType.CHEST_3_ROW,
                        isPerPlayer = false, // A new Menu is created for each player, so we do not need this option
                        allowSpectatorClicks = false)
                menu.onClosed { player ->
                    onClosed(player)
                }
                menu
            }
        }

        override val viewers = mutableSetOf<Player>()
        override val openSound = Sound.sound(SoundEvent.BLOCK_ENDER_CHEST_OPEN, Sound.Source.BLOCK, 1f, 1f)
        override val closeSound = Sound.sound(SoundEvent.BLOCK_ENDER_CHEST_CLOSE, Sound.Source.BLOCK, 1f, 1f)
    }

    class Chest(
        private val game: Game,
        inventoryType: InventoryType,
        override val instance: Instance,
        override val position: Point,
    ) : ChestBlock() {

        private val menu by lazy {
            game.getModule<GuiModule>().createMenu(
                Component.translatable(
                    if (inventoryType == InventoryType.CHEST_6_ROW) "container.chestDouble" else "container.chest"
                ), inventoryType, isPerPlayer = false, allowSpectatorClicks = false
            ).apply {
                onClosed { player -> onClosed(player) }
            }
        }

        override fun getMenu(player: Player) = menu

        override val viewers = mutableSetOf<Player>()
        override val openSound = Sound.sound(SoundEvent.BLOCK_CHEST_OPEN, Sound.Source.BLOCK, 1f, 1f)
        override val closeSound = Sound.sound(SoundEvent.BLOCK_CHEST_CLOSE, Sound.Source.BLOCK, 1f, 1f)
    }

    private fun getRootChest(instance: Instance, pos: Point): Pair<InventoryType, Point> {
        val nearbyChests = listOf(
            pos.add(1.0, 0.0, 0.0),
            pos.add(-1.0, 0.0, 0.0),
            pos.add(0.0, 0.0, 1.0),
            pos.add(0.0, 0.0, -1.0),
        ).filter { adjacent ->
            instance.getBlock(adjacent).compare(Block.CHEST)
        }
        val inventoryType = if (nearbyChests.isNotEmpty()) InventoryType.CHEST_6_ROW else InventoryType.CHEST_3_ROW
        val rootPosition =
            nearbyChests.filter { chests.containsKey(it) }.sortedBy { it.blockX() }.maxByOrNull { it.blockZ() } ?: pos
        return inventoryType to rootPosition
    }
}