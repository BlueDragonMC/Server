package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.*
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.model.PlayerDocument
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.minigame.SpectatorModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.*
import kotlinx.coroutines.launch
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.sound.SoundEvent
import java.time.Duration

class AwardsModule : GameModule() {

    private lateinit var parent: Game
    private val postGameAwards = mutableMapOf<PlayerAwardReason, Int>()

    private data class PlayerAwardReason(val player: Player, val reason: Component)

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent

        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            distributePostGameAwards(event.player)
        }

        eventNode.addListener(SpectatorModule.StartSpectatingEvent::class.java) { event ->
            distributePostGameAwards(event.player)
        }

        eventNode.addListener(WinModule.WinnerDeclaredEvent::class.java) { _ ->
            parent.players.forEach { player ->
                distributePostGameAwards(player)
            }
        }
    }

    fun awardCoins(player: Player, amount: Int, reason: Component) {
        addCoins(player, amount)
        sendAwardMessage(player, amount, reason)
    }

    /**
     * Distributes the specified award when the game ends or the player is eliminated (they leave or become a spectator).
     */
    fun awardCoinsAfterGame(player: Player, amount: Int, reason: Component) {
        if (parent.state == GameState.ENDING) {
            awardCoins(player, amount, reason)
            return
        }
        val reasonStruct = PlayerAwardReason(player, reason)
        postGameAwards[reasonStruct] = postGameAwards.getOrDefault(reasonStruct, 0) + amount
    }

    private fun notifyLevelUp(player: CustomPlayer, oldLevel: Int, newLevel: Int) {
        player.showTitle(
            Title.title(
                Component.text("LEVEL UP!").withGradient(ALT_COLOR_1, ALT_COLOR_2).withDecoration(TextDecoration.BOLD),
                Component.text("You are now level ", ALT_COLOR_1) + Component.text(newLevel)
            )
        )
        val msg = buildComponent {
            +Component.translatable("module.award.level_up.1", ALT_COLOR_1)
            +Component.newline()
            +Component.translatable("module.award.level_up.2", Component.text(oldLevel, ALT_COLOR_2), Component.text(newLevel, ALT_COLOR_2))
        }
        player.sendMessage(msg.surroundWithSeparators())
        player.playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 1.0F, 1.0F))
    }

    private fun addCoins(player: Player, amount: Int) {
        player as CustomPlayer
        require(player.isDataInitialized()) { "Player's data has not loaded!" }
        Database.IO.launch {
            player.data.compute(PlayerDocument::coins) { it + amount }
            val prev = player.data.compute(PlayerDocument::experience) { it + amount }
            val oldLevel = CustomPlayer.getXpLevel(prev).toInt()
            val newLevel = CustomPlayer.getXpLevel(player.data.experience).toInt()
            if (newLevel > oldLevel)
                MinecraftServer.getSchedulerManager().buildTask { notifyLevelUp(player, oldLevel, newLevel) }
                    .delay(Duration.ofSeconds(2)).schedule()
        }
        Database.IO.launch {
            Messaging.outgoing.recordCoinAward(player.uuid, amount, parent.id)
        }
    }

    private fun sendAwardMessage(player: Player, amount: Int, reason: Component) {
        player.sendMessage(
            Component.translatable("module.award.awarded_coins", ALT_COLOR_2, Component.text(amount), reason)
        )
    }

    private fun distributePostGameAwards(player: Player) {
        synchronized(postGameAwards) {
            var total = 0
            postGameAwards.entries.removeAll { (award, amount) ->
                if (award.player != player) return@removeAll false
                total += amount
                sendAwardMessage(award.player, amount, award.reason)
                true
            }
            addCoins(player, total)
        }
    }

    companion object {
        val AWARD_REASON_KILLS = Component.translatable("module.award.reason.kills")
    }
}
