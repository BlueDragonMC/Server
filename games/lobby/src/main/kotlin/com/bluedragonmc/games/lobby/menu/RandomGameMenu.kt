package com.bluedragonmc.games.lobby.menu

import com.bluedragonmc.games.lobby.GameEntry
import com.bluedragonmc.games.lobby.Lobby
import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.CircularList
import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import kotlin.random.Random

class RandomGameMenu(private val games: List<GameEntry>, private val parent: Lobby) : Lobby.LobbyMenu() {

    override lateinit var menu: GuiModule.Menu

    private val glassPanes = listOf(
        // All glass pane types (excluding light gray)
        Material.WHITE_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE,
        Material.MAGENTA_STAINED_GLASS_PANE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE,
        Material.PINK_STAINED_GLASS_PANE,
        Material.GRAY_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.BROWN_STAINED_GLASS_PANE,
        Material.GREEN_STAINED_GLASS_PANE,
        Material.RED_STAINED_GLASS_PANE,
        Material.BLACK_STAINED_GLASS_PANE,
    ).map {
        ItemStack.builder(it).displayName(Component.empty()).build()
    }

    private val frames = mutableMapOf<Player, Int>()
    private val offsets = mutableMapOf<Player, Int>()

    override fun populate() {
        menu = parent.getModule<GuiModule>()
            .createMenu(Component.translatable("lobby.menu.random_game.title"), InventoryType.CHEST_3_ROW, true, true)

        menu.onOpened { player ->
            frames[player] = 0
            offsets[player] = Random.nextInt(0, games.size)
        }

        menu.onTick { player ->
            if (player.aliveTicks % 3 == 0L) update(player)
        }

        menu.onClosed { player ->
            frames.remove(player)
            offsets.remove(player)
        }
    }

    private fun update(player: Player) {
        val frame = frames[player]!!
        val offset = offsets[player]!!
        frames[player] = frame + 1

        val circularList = CircularList(games)

        player.playSound(
            Sound.sound(
                SoundEvent.BLOCK_NOTE_BLOCK_BASS,
                Sound.Source.BLOCK,
                1.0f,
                0.5f + (0.1f * frame)
            )
        )

        if (frame in 15 .. 20) {
            val game = circularList[15 + 3 + offset] // Always use the choice from the 15th frame to prevent it from changing
            for (i in 0 until 27) {
                menu.setItemStack(player, i, glassPanes[frame - 15])
            }
            menu.setItemStack(player, 13, ItemStack.builder(game.material).displayName(Component.text(game.game, ALT_COLOR_1).noItalic()).build())
            return
        } else if (frame >= 20) {
            val game = circularList[15 + 3 + offset]
            Environment.current.queue.queue(player, GameType(game.game, null, null))
            menu.close(player)
            return
        }

        for (i in 0 until 27) {
            menu.setItemStack(player, i, glassPanes.random())
        }

        val radius = (7 - (frame / 2 * 2)).coerceAtLeast(1)
        val startingSlot = 13 - radius / 2
        var i = frame
        while (true) {
            val game = circularList[i + offset]
            if (i >= radius + frame) break
            val item = ItemStack.builder(game.material)
                .displayName(Component.text(game.game, ALT_COLOR_1).noItalic()).build()
            menu.setItemStack(player, startingSlot + i - frame, item)
            i++
        }
    }
}