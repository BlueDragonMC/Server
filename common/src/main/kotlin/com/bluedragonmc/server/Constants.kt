package com.bluedragonmc.server

import com.bluedragonmc.server.utils.withGradient
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import java.io.File
import java.util.*

/**
 * The namespace used for custom
 * BlueDragon registry keys.
 */
const val NAMESPACE = "bluedragon"

/**
 * The default locale used when flattening
 * and translating components.
 */
val DEFAULT_LOCALE: Locale = Locale.ENGLISH

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
val ALT_COLOR_1: TextColor = NamedTextColor.YELLOW

/**
 * A secondary alternate color that complements [ALT_COLOR_1].
 */
val ALT_COLOR_2: TextColor = NamedTextColor.GOLD

/**
 * The name of the server ("BlueDragon") with a nice gradient. This is not bold.
 */
val SERVER_NAME_GRADIENT = Component.text("BlueDragon").withGradient(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_3)

/**
 * A base64-encoded PNG image of the server's favicon shown on clients' server lists.
 */
val FAVICON = "data:image/png;base64," + runCatching {
    Base64.getEncoder().encode(File("favicon_64.png").readBytes())
}.getOrElse { "" }