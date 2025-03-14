package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.database.StatisticsModule.OrderBy
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Permissions
import com.bluedragonmc.server.utils.plus
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player

class LeaderboardCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    val keyArgument by WordArgument
    usage(usageString)

    syntax(keyArgument) {
        val key = get(keyArgument)
        val game = if (sender is Player) Game.findGame(player) ?: lobby else lobby
        Database.IO.launch {
            val leaderboard = game.getModule<StatisticsModule>()
                .rankPlayersByStatistic(key, OrderBy.DESC, limit = 10)
            leaderboard.forEach { (doc, value) ->
                val color = Permissions.getMetadata(doc.uuid).rankColor
                val formattedName = Component.text(doc.username, color)
                sender.sendMessage(formattedName + Component.text(" - ", BRAND_COLOR_PRIMARY_2) + Component.text(value, BRAND_COLOR_PRIMARY_1))
            }
        }
    }
})