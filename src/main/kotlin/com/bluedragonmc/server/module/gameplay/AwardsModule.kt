package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.PlayerDocument
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

class AwardsModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {}

    fun awardCoins(player: Player, amount: Int, reason: String) =
        awardCoins(player, amount, Component.text(reason, NamedTextColor.GOLD))

    fun awardCoins(player: Player, amount: Int, reason: Component) {
        player as CustomPlayer
        require(player.isDataInitialized()) { "Player's data has not loaded!" }
        DatabaseModule.IO.launch {
            player.data.compute(PlayerDocument::coins) { it + amount }
        }
        player.sendMessage(Component.text("+$amount coins (", NamedTextColor.GOLD).append(reason)
            .append(Component.text(")", NamedTextColor.GOLD)))
    }
}
