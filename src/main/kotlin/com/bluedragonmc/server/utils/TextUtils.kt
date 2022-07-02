package com.bluedragonmc.server.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

private val separator
    get() = Component.text("=================================", NamedTextColor.WHITE, TextDecoration.STRIKETHROUGH)

fun Component?.surroundWithSeparators(): Component {
    if (this == null) return Component.empty()
    return separator.append(Component.newline())
        .append(this.decoration(TextDecoration.STRIKETHROUGH, false))
        .append(Component.newline()).append(separator)
}

val miniMessage = MiniMessage.miniMessage()
fun String.asTextComponent() = miniMessage.deserialize(this)
fun Component.toPlainText() = PlainTextComponentSerializer.plainText().serialize(this)