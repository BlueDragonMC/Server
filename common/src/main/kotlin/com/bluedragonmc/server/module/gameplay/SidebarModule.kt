package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.*
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.withGradient
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.scoreboard.Sidebar.ScoreboardLine
import java.util.*

/**
 * A module that shows a per-player sidebar to all players in the game.
 * Sidebars are created by adding "bindings", which can be updated at
 * any point. These bindings supply a list of [Component] which is used
 * to re-render the scoreboard.
 */
class SidebarModule(private val title: String) : GameModule() {

    private lateinit var parent: Game
    private val sidebars = mutableMapOf<Player, Sidebar>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        parent.players.forEach { player ->
            sidebars[player] = createSidebar().apply { addViewer(player) }
            if (::binding.isInitialized)
                binding.updateFor(player)
        }
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            val sidebar = sidebars.getOrPut(event.player) { createSidebar() }
            sidebar.addViewer(event.player)
            if (::binding.isInitialized)
                binding.updateFor(event.player)
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            sidebars[event.player]?.removeViewer(event.player)
            sidebars.remove(event.player)
        }
    }

    private lateinit var binding: ScoreboardBinding

    /**
     * Creates a scoreboard binding.
     * Games may only have **one** scoreboard binding. Updating a binding overrides all other bindings.
     */
    fun bind(block: ScoreboardBinding.ScoreboardBindingUtils.(Player) -> Collection<Component>): ScoreboardBinding {
        check(::parent.isInitialized) { "bind() was called before the SidebarModule was initialized!" }
        check(!::binding.isInitialized) { "Only one scoreboard binding can be created per SidebarModule!" }
        binding = ScoreboardBinding(block, this)
        return binding
    }

    private fun createSidebar() = Sidebar(text(title.uppercase(), ALT_COLOR_1, TextDecoration.BOLD))

    data class ScoreboardBinding(
        private val updateFunction: ScoreboardBindingUtils.(Player) -> Collection<Component>,
        private val module: SidebarModule,
    ) {

        inner class ScoreboardBindingUtils {
            private var spaces = 1
            fun getSpacer() = text(" ".repeat(spaces++))
        }

        fun update() {
            module.parent.players.forEach { player ->
                updateFor(player)
            }
        }

        private companion object {
            private val HEADER = setOf(
                text(Calendar.getInstance().run {
                    val serverId = runBlocking { Environment.getServerName().substringAfter("-") }
                    listOf(
                        get(Calendar.MONTH) + 1,
                        get(Calendar.DAY_OF_MONTH),
                        get(Calendar.YEAR).toString().takeLast(2)
                    ).joinToString("/") + " · " + serverId
                }, DARK_GRAY)
            )
            private val ipGradient = text(SERVER_IP).withGradient(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_2)
            private val FOOTER = if (Environment.current::class.simpleName?.contains("Development") == true)
                listOf(ipGradient, text("Development Version", RED)) else listOf(ipGradient)
        }

        internal fun updateFor(player: Player) {
            val lines = HEADER + updateFunction(ScoreboardBindingUtils(), player) + FOOTER
            val old = module.sidebars[player] ?: module.createSidebar()

            if (old.lines.size == lines.size) {
                updateExisting(old, lines)
            } else {
                // Re-create the sidebar as its size has changed.
                val new = module.createSidebar()
                lines.forEachIndexed { i, line -> new.createLine(ScoreboardLine("line-$i", line, lines.size - i)) }
                old.viewers.forEach { new.addViewer(it) } // Re-add all existing viewers
                module.sidebars[player] = new
            }
        }

        private fun updateExisting(sidebar: Sidebar, lines: Collection<Component>) {
            check(lines.size == sidebar.lines.size) { "Cannot update a sidebar with a reference of differing length." }
            // Override all existing lines
            lines.forEachIndexed { i, line ->
                if (sidebar.getLine("line-$i")!!.content != line) {
                    sidebar.updateLineContent("line-$i", line)
                }
            }
        }

        init {
            update()
        }
    }
}