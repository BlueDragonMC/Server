package com.bluedragonmc.server.game.lobby

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_3
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.LOBBY_NEWS_ITEMS
import com.bluedragonmc.server.game.Lobby
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.utils.MapUtils
import kotlinx.coroutines.runBlocking
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.metadata.other.ItemFrameMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.timer.ExecutionType
import org.spongepowered.configurate.ConfigurationNode
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.time.Duration
import javax.imageio.ImageIO

class LeaderboardsModule(config: ConfigurationNode) : GameModule() {

    private val leaderboards = config.node("leaderboards").getList(Leaderboard::class.java)

    // Font is from https://www.1001freefonts.com/minecraft.font
    private val baseFont = Font.createFont(Font.TRUETYPE_FONT, this::class.java.getResourceAsStream("/font/Minecraft.otf"))
    private val font28 = baseFont.deriveFont(Font.PLAIN, 28f)
    private val font36 = baseFont.deriveFont(Font.PLAIN, 36f)
    private val font72 = baseFont.deriveFont(Font.PLAIN, 72f)

    // Create a list of font sizes to use for dynamic text scaling
    private val fontSizes = (12 .. 36 step 2).map { baseFont.deriveFont(Font.PLAIN, it.toFloat()) }

    private val gray = 0x727272

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        // Create ad board once
        createAdBoard(parent)
        // Refresh leaderboards every 5 minutes
        MinecraftServer.getSchedulerManager().buildTask {
            val start = System.nanoTime()
            leaderboards?.forEachIndexed { lbIndex, lb ->
                createLeaderboard(parent, lbIndex, lb)
            }
            logger.info("Created ${leaderboards?.size} leaderboard displays in ${(System.nanoTime() - start) / 1_000_000}ms.")
        }.repeat(Duration.ofMinutes(5)).executionType(ExecutionType.ASYNC).schedule()
    }

    private fun createAdBoard(parent: Game) {
        MapUtils.createMaps(parent.getInstance(), Pos(-19.0, 64.0, -17.0), Pos(-19.0, 62.0, -23.0), ItemFrameMeta.Orientation.EAST) { graphics ->
            val imageStream = Lobby::class.java.getResourceAsStream("/bd-banner.png")!!
            val image = ImageIO.read(imageStream)
            val scale = (128 * 7) / image.width.toDouble()
            graphics.background = Color.WHITE
            graphics.clearRect(0, 0, 128 * 7, 128 * 3)
            graphics.drawRenderedImage(image, AffineTransform.getScaleInstance(scale, scale))
            graphics.font = font36
            graphics.color = Color.BLACK
            LOBBY_NEWS_ITEMS.forEachIndexed { index, str ->
                graphics.drawString(str, 10, 200 + index * 30)
            }
            graphics.drawString("Join our community at bluedragonmc.com", 10f, 128f * 3f - 10f, Color(BRAND_COLOR_PRIMARY_3.value()), font28)
        }
    }

    private fun createLeaderboard(parent: Game, lbIndex: Int, lb: Leaderboard) {
        MapUtils.createMaps(parent.getInstance(), lb.topLeft, lb.bottomRight, lb.orientation, 5000 + lbIndex * 200) { graphics ->
            graphics.drawString(lb.title, 10f, 70f, Color.WHITE, font72)
            graphics.drawString(lb.subtitle, 10f, 110f, Color(gray), font36)
            runBlocking {
                val leaderboardPlayers = parent.getModule<StatisticsModule>().rankPlayersByStatistic(lb.statistic, lb.orderBy, 10)
                val it = leaderboardPlayers.entries.iterator()
                for (i in 1 .. lb.show) {
                    // Draw leaderboard numbers (default: 1-10)
                    val lineStartY = 130f + 30f * i
                    graphics.drawString("$i.", 10f, lineStartY, Color.WHITE, font36)

                    if (!it.hasNext()) continue
                    val (player, value) = it.next()

                    // Create the text to display based on the display mode
                    val displayText = LeaderboardBrowser.formatValue(value, lb.displayMode)
                    // Draw player name
                    val remainingPixels = 128 * 4 - stringWidth(graphics, font36, displayText) - 60f - 10f
                    val playerNameFont = fontSizes.lastOrNull {
                        stringWidth(graphics, it, player.username) < remainingPixels
                    } ?: continue
                    val nameColor = Color(player.highestGroup?.color?.value() ?: gray)
                    graphics.drawString(player.username, 60f, lineStartY, nameColor, playerNameFont)
                    // Draw leaderboard value
                    graphics.drawString(displayText, 128 * 4 - stringWidth(graphics, displayText) - 10f, lineStartY, Color.WHITE, font36)
                }
            }
        }
    }

    private fun Graphics2D.drawString(string: String, x: Float, y: Float, color: Color, font: Font) {
        this.font = font
        this.color = color
        drawString(string, x, y)
    }

    private fun stringWidth(graphics: Graphics2D, string: String) =
        graphics.font.getStringBounds(string, FontRenderContext(graphics.transform, false, false)).width.toFloat()

    private fun stringWidth(graphics: Graphics2D, font: Font, string: String) =
        font.getStringBounds(string, FontRenderContext(graphics.transform, false, false)).width.toFloat()
}