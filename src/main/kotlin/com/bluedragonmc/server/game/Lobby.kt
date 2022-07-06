package com.bluedragonmc.server.game

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

class Lobby : Game("Lobby") {
    init {
        // World modules
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/lobbyv2.1")))
        use(SharedInstanceModule())

        // Player modules
        use(VoidDeathModule(32.0))
        use(InstantRespawnModule())
        use(SpawnpointModule(SpawnpointModule.SingleSpawnpointModule(Pos(0.5, 64.0, 0.5, 180F, 0F))))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false, forcedItemSlot = null))
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = false))

        // NPCs
        use(NPCModule())
        // 0.5, 62.5, -35.5, 0.0, 0.0 CENTER
        getModule<NPCModule>().addNPC(instance = this.getInstance(), position = Pos(0.5, 62.5, -35.5, 0F, 0F), customName = Component.text("WackyMaze", NamedTextColor.YELLOW, TextDecoration.BOLD), skin = NPCModule.NPCSkins.EX4.skin, interaction = {
            queue.queue(it.player, "WackyMaze")
        })

        ready()
    }
}