package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.gameplay.SpectatorModule
import com.bluedragonmc.server.utils.SingleAssignmentProperty
import com.bluedragonmc.server.utils.TextUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.time.Duration

class WinModule(val winCondition: WinCondition = WinCondition.MANUAL) : GameModule() {
    private var parent by SingleAssignmentProperty<Game>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(SpectatorModule.StartSpectatingEvent::class.java) {
            val spectatorModule = parent.getModule<SpectatorModule>()
            if (winCondition == WinCondition.LAST_ALIVE && parent.players.size - spectatorModule.spectatorCount() <= 1) {
                for (player in parent.players) {
                    if (!spectatorModule.isSpectating(player)) {
                        declareWinner(player)
                        break
                    }
                }
            }
        }
    }

    fun declareWinner(winner: Component, player: Player? = null) {
        parent.sendMessage(TextUtils.surroundWithSeparators(winner.append(Component.text(" won the game!", NamedTextColor.DARK_AQUA))))
        for (p in parent.players) {
            if (player?.uuid == p.uuid) p.showTitle(Title.title(
                Component.text("VICTORY!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.empty()))
            else p.showTitle(Title.title(
                Component.text("GAME OVER!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Better luck next time!", NamedTextColor.RED)))
        }
        parent.endGame(Duration.ofSeconds(5))
    }

    fun declareWinner(winner: Player) {
        declareWinner(winner.name, winner)
    }

    enum class WinCondition {
        /**
         * No automatic win condition. Use the `declareWinner` function to manually declare the winner.
         */
        MANUAL,

        /**
         * Automatically declare the winner as the last non-spectating player. Requires the `SpectatorModule` to be active.
         */
        LAST_ALIVE,
    }

}