package com.bluedragonmc.server.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.item.Material

private val separator
    get() = Component.text("=================================", NamedTextColor.WHITE, TextDecoration.STRIKETHROUGH)

fun Component?.surroundWithSeparators(): Component {
    if (this == null) return Component.empty()
    return separator.append(Component.newline()).append(this.decoration(TextDecoration.STRIKETHROUGH, false))
        .append(Component.newline()).append(separator)
}

val miniMessage = MiniMessage.miniMessage()
fun String.asTextComponent() = miniMessage.deserialize(this)
fun Component.toPlainText() = PlainTextComponentSerializer.plainText().serialize(this)
operator fun Component.plus(component: Component) = append(component)
fun Component.hoverEvent(text: String, color: NamedTextColor): Component =
    hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(text, color)))

fun Component.clickEvent(command: String): Component =
    clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, command))

fun Component.clickEvent(action: ClickEvent.Action, value: String) = clickEvent(ClickEvent.clickEvent(action, value))

fun Material.displayName() = Component.translatable(registry().translationKey())
fun Material.displayName(color: TextColor) = Component.translatable(registry().translationKey(), color)

fun Component.noItalic() = decoration(TextDecoration.ITALIC, false)

class ComponentBuilder {

    private val list = mutableListOf<Component>()

    operator fun Component.unaryPlus() {
        list.add(this)
    }

    fun build() = Component.join(JoinConfiguration.noSeparators(), list)
}

inline fun buildComponent(block: ComponentBuilder.() -> Unit) = ComponentBuilder().apply(block).build()