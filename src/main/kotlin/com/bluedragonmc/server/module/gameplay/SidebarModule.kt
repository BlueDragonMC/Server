package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.SERVER_IP
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.scoreboard.Sidebar.ScoreboardLine

/**
 * A module that shows a sidebar to all players in the game.
 */
class SidebarModule(title: String) : GameModule() {
    private lateinit var parent: Game
    private val sidebar: Sidebar = Sidebar(Component.text(title.uppercase(), ALT_COLOR_1, TextDecoration.BOLD))
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        sidebar.createLine(ScoreboardLine("website", SERVER_IP withColor BRAND_COLOR_PRIMARY_1, 0))
        parent.players.forEach { sidebar.addViewer(it) }
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            sidebar.addViewer(event.player)
        }
        eventNode.addListener(RemoveEntityFromInstanceEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener
            sidebar.removeViewer(event.entity as Player)
        }
    }

    fun bind(block: () -> Collection<Pair<String, Component>>) = ScoreboardBinding(block, this)

    fun addLines(lines: Collection<Pair<String, Component>>) {
        addLines(lines.reversed().mapIndexed { i, it -> ScoreboardLine(it.first, it.second, sidebar.lines.size + i) })
    }

    fun addLines(lines: List<ScoreboardLine>) {
        lines.forEach {
            if (sidebar.getLine(it.id) == null) sidebar.createLine(it)
            else {
                sidebar.updateLineContent(it.id, it.content)
                if (sidebar.getLine(it.id)!!.line != it.line)
                    sidebar.updateLineScore(it.id, it.line)
            }
        }
    }

    data class ScoreboardBinding(
        private val updateFunction: () -> Collection<Pair<String, Component>>,
        private val module: SidebarModule
    ) {
        fun update() = module.addLines(updateFunction())

        init {
            update()
        }
    }

    /**
     * Adds a new line above all existing lines.
     */
    fun addLine(id: String, line: Component) {
        sidebar.createLine(ScoreboardLine(id, line, sidebar.lines.size))
    }

    /**
     * Update an existing line based on its ID.
     */
    fun updateLine(id: String, newLine: Component) {
        sidebar.updateLineContent(id, newLine)
    }
}