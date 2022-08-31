package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.utils.plus
import com.mongodb.internal.operation.OrderBy
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player

class LeaderboardCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    val keyArgument by WordArgument
    usage(usageString)

    syntax(keyArgument) {
        val key = get(keyArgument)
        val game = if(sender is Player) Game.findGame(sender) ?: lobby else lobby
        DatabaseModule.IO.launch {
            val leaderboard = game.getModule<StatisticsModule>()
                .rankPlayersByStatistic(key, OrderBy.DESC, limit = 10)
            leaderboard.forEach { (doc, value) ->
                val formattedName = Component.text(doc.username, doc.highestGroup?.color ?: NamedTextColor.GRAY)
                sender.sendMessage(formattedName + Component.text(" - ", BRAND_COLOR_PRIMARY_2) + Component.text(value, BRAND_COLOR_PRIMARY_1))
            }
        }
    }
})