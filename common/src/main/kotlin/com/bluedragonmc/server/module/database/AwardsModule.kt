package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.*
import com.bluedragonmc.server.model.PlayerDocument
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.module.GameModule
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

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {}

    fun awardCoins(player: Player, amount: Int, reason: Component) {
        player as CustomPlayer
        require(player.isDataInitialized()) { "Player's data has not loaded!" }
        val oldLevel = CustomPlayer.getXpLevel(player.data.experience).toInt()
        Database.IO.launch {
            player.data.compute(PlayerDocument::coins) { it + amount }
            player.data.compute(PlayerDocument::experience) { it + amount }
            val newLevel = CustomPlayer.getXpLevel(player.data.experience).toInt()
            if (newLevel > oldLevel)
                MinecraftServer.getSchedulerManager().buildTask { notifyLevelUp(player, oldLevel, newLevel) }
                    .delay(Duration.ofSeconds(2)).schedule()
        }
        player.sendMessage(
            Component.translatable("module.award.awarded_coins", ALT_COLOR_2, Component.text(amount), reason)
        )
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
}
