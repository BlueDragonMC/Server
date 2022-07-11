package com.bluedragonmc.server.game

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.queue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import java.nio.file.Paths

class Lobby : Game("Lobby", "lobbyv2.1") {
    init {
        // World modules
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(SharedInstanceModule())

        // Player modules
        use(VoidDeathModule(32.0))
        use(InstantRespawnModule())
        use(SpawnpointModule(SpawnpointModule.SingleSpawnpointProvider(Pos(0.5, 64.0, 0.5, 180F, 0F))))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false, forcedItemSlot = null))
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = false))

        // NPCs
        use(NPCModule())
        // 0.5, 62.5, -35.5, 0.0, 0.0 CENTER
        getModule<NPCModule>().addNPC(instance = this.getInstance(), position = Pos(0.5, 62.5, -35.5, 0F, 0F), customName = Component.text("WackyMaze", NamedTextColor.YELLOW, TextDecoration.BOLD), skin = NPCModule.NPCSkins.EX4.skin, interaction = {
            queue.queue(it.player, GameType("WackyMaze", null, "Islands"))
        })
        // -3.5, 62.5, -34.5, 0.0, 0.0 LEFT OF CENTER
        getModule<NPCModule>().addNPC(instance = this.getInstance(), position = Pos(-3.5, 62.5, -34.5, 0F, 0F), customName = Component.text("BedWars", NamedTextColor.YELLOW, TextDecoration.BOLD), skin = NPCModule.NPCSkins.SKY.skin, interaction = {
            queue.queue(it.player, GameType("BedWars", null, "Caves"))
        })
        ready()
    }
}