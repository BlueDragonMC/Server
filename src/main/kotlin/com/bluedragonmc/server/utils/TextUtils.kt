package com.bluedragonmc.server.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

object TextUtils {
    private val separator
        get() = Component.text("=================================", NamedTextColor.WHITE, TextDecoration.STRIKETHROUGH)

    fun surroundWithSeparators(input: Component?): Component {
        if (input == null) return Component.empty()
        return separator.append(Component.newline())
            .append(input.decoration(TextDecoration.STRIKETHROUGH, false))
            .append(Component.newline()).append(separator)
    }
}