package com.bluedragonmc.server

import com.bluedragonmc.server.utils.noBold
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * Light color, often used for emphasis.
 */
val BRAND_COLOR_PRIMARY_1 = TextColor.color(0x4EB2F4)

/**
 * Medium color, often used for chat messages.
 */
val BRAND_COLOR_PRIMARY_2 = TextColor.color(0x2792f7) // Medium, often used for chat messages

/**
 * Very dark color.
 */
val BRAND_COLOR_PRIMARY_3 = TextColor.color(0x3336f4) // Very dark

/**
 * An alternate color, used for the title in the scoreboard and some chat messages.
 */
val ALT_COLOR_1 = NamedTextColor.YELLOW

/**
 * The hostname of the server, used in the scoreboard footer.
 */
const val SERVER_IP = "bluedragonmc.com"

/**
 * Information about the latest update, displayed in the server list description.
 */
val SERVER_NEWS = Component.text("              NEW GAME", NamedTextColor.GOLD, TextDecoration.BOLD) +
        Component.text(" - ", NamedTextColor.GRAY).noBold() +
        Component.text("SKYWARS", NamedTextColor.YELLOW, TextDecoration.BOLD)