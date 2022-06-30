package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.gameplay.MOTDModule
import com.bluedragonmc.server.utils.SingleAssignmentProperty
import net.kyori.adventure.text.Component
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * This module does not contain any unique behavior. It automatically loads the following modules:
 * - `CountdownModule`
 * - `WinModule`
 * - `MOTDModule`
 */
class MiniGameModule(
    private val countdownThreshold: Int,
    private val winCondition: WinModule.WinCondition = WinModule.WinCondition.MANUAL,
    private val motd: Component
) : GameModule() {
    private var parent by SingleAssignmentProperty<Game>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        parent.use(CountdownModule(threshold = countdownThreshold))
        parent.use(WinModule(winCondition = winCondition))
        parent.use(MOTDModule(motd = motd))
    }

    override fun deinitialize() {
        parent.unregister(parent.getModule<CountdownModule>())
        parent.unregister(parent.getModule<WinModule>())
        parent.unregister(parent.getModule<MOTDModule>())
        super.deinitialize()
    }
}
