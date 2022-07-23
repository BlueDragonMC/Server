package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.ALT_COLOR_2
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.PlayerDocument
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.surroundWithSeparators
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

    override val dependencies = listOf(DatabaseModule::class)

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {}

    fun awardCoins(player: Player, amount: Int, reason: String) =
        awardCoins(player, amount, Component.text(reason, ALT_COLOR_2))

    fun awardCoins(player: Player, amount: Int, reason: Component) {
        player as CustomPlayer
        require(player.isDataInitialized()) { "Player's data has not loaded!" }
        val oldLevel = CustomPlayer.getXpLevel(player.data.experience).toInt()
        DatabaseModule.IO.launch {
            player.data.compute(PlayerDocument::coins) { it + amount }
            player.data.compute(PlayerDocument::experience) { it + amount }
            val newLevel = CustomPlayer.getXpLevel(player.data.experience).toInt()
            if (newLevel > oldLevel)
                MinecraftServer.getSchedulerManager().buildTask { notifyLevelUp(player, oldLevel, newLevel) }
                    .delay(Duration.ofSeconds(2)).schedule()
        }
        player.sendMessage(
            Component.text("+$amount coins (", ALT_COLOR_2).append(reason)
                .append(Component.text(")", ALT_COLOR_2))
        )
    }

    private fun notifyLevelUp(player: CustomPlayer, oldLevel: Int, newLevel: Int) {
        player.showTitle(
            Title.title(
                Component.text("LEVEL UP!", ALT_COLOR_2, TextDecoration.BOLD),
                Component.text("You are now level ", ALT_COLOR_1) + Component.text(newLevel)
            )
        )
        player.sendMessage(
            (Component.text("Level up!\n", ALT_COLOR_1) + Component.text(
                oldLevel,
                ALT_COLOR_2
            ) + Component.text(" → ", ALT_COLOR_1) + Component.text(newLevel, ALT_COLOR_2)).surroundWithSeparators()
        )
        player.playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 1.0F, 1.0F))
    }
}
