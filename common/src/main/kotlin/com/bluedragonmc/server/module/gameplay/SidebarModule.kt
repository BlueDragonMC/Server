package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.*
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.event.CountdownEvent
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.withGradient
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.translation.GlobalTranslator
import net.minestom.server.MinecraftServer
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

            fun getStatusSection() = when (module.parent.state) {
                GameState.SERVER_STARTING -> listOf(
                    getSpacer(),
                    Component.translatable("module.sidebar.server_starting", BRAND_COLOR_PRIMARY_2),
                    getSpacer()
                )

                GameState.WAITING -> listOf(
                    getSpacer(),
                    Component.translatable("module.sidebar.waiting", BRAND_COLOR_PRIMARY_2),
                    getSpacer()
                )

                GameState.STARTING -> listOf(
                    getSpacer(),
                    Component.translatable("module.sidebar.starting", BRAND_COLOR_PRIMARY_2),
                    getSpacer()
                )

                GameState.INGAME, GameState.ENDING -> listOf(getSpacer())
            }
        }

        fun update() {
            module.parent.players.forEach { player ->
                updateFor(player)
            }
        }

        private companion object {

            private fun getHeader(game: Game): Iterable<Component> {
                val dateString = Calendar.getInstance().run {
                    listOf(
                        get(Calendar.MONTH) + 1,
                        get(Calendar.DAY_OF_MONTH),
                        get(Calendar.YEAR).toString().takeLast(2)
                    ).joinToString("/")
                }
                val serverId = runBlocking { Environment.getServerName().substringAfter("-") }
                return setOf(text("$dateString · $serverId · ${game.id}", DARK_GRAY))
            }

            private fun getFooter(player: Player): Iterable<Component> {
                val ipGradient = GlobalTranslator.render(
                    Component.translatable("global.server.domain.stylized"), player.locale ?: DEFAULT_LOCALE
                ).withGradient(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_2)

                return if (Environment.current.isDev) {
                    setOf(text("⚠ Development Version", RED), ipGradient)
                } else {
                    setOf(ipGradient)
                }
            }
        }

        internal fun updateFor(player: Player) {
            val lines = getHeader(module.parent) + updateFunction(ScoreboardBindingUtils(), player) + getFooter(player)
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
            val updateNextTick = {
                MinecraftServer.getSchedulerManager().scheduleNextTick(::update)
            }

            updateNextTick()

            module.eventNode.addListener(GameStateChangedEvent::class.java) { _ -> updateNextTick() }
            module.eventNode.addListener(GameStartEvent::class.java) { _ -> updateNextTick() }
            module.eventNode.addListener(CountdownEvent.CountdownStartEvent::class.java) { _ -> update() }
            module.eventNode.addListener(CountdownEvent.CountdownTickEvent::class.java) { _ -> update() }
        }
    }
}