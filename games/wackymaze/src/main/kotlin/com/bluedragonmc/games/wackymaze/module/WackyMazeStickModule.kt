package com.bluedragonmc.games.wackymaze.module

import com.bluedragonmc.games.wackymaze.WackyMazeGame
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.utils.ItemUtils
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.item.Material

@DependsOn(CosmeticsModule::class)
class WackyMazeStickModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) {
            parent.players.forEach { player ->
                val material =
                    parent.getModule<CosmeticsModule>().getCosmeticInGroup<WackyMazeGame.StickItem>(player)?.material
                        ?: Material.STICK
                player.inventory.setItemStack(0, ItemUtils.knockbackStick(10, player).withMaterial(material))
            }
        }
    }
}