package com.bluedragonmc.server.utils

import com.bluedragonmc.server.ALT_COLOR_1
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.flattener.ComponentFlattener
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.item.Material

private val separator
    get() = Component.text("=================================", NamedTextColor.WHITE, TextDecoration.STRIKETHROUGH)

fun Component?.surroundWithSeparators(): Component {
    if (this == null) return Component.empty()
    return separator.append(Component.newline()).append(this.decoration(TextDecoration.STRIKETHROUGH, false))
        .append(Component.newline()).append(separator)
}

fun Component.toPlainText() = PlainTextComponentSerializer.plainText().serialize(this)
operator fun Component.plus(component: Component) = append(component)
fun Component.hoverEvent(text: String, color: NamedTextColor): Component =
    hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(text, color)))

fun Component.clickEvent(command: String): Component =
    clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, command))

fun Component.clickEvent(action: ClickEvent.Action, value: String): Component {
    if (action == ClickEvent.Action.COPY_TO_CLIPBOARD && hoverEvent() == null) {
        return clickEvent(ClickEvent.clickEvent(action, value)).hoverEvent("Click to copy!", ALT_COLOR_1)
    }
    return clickEvent(ClickEvent.clickEvent(action, value))
}

fun Material.displayName() = Component.translatable(registry().translationKey())
fun Material.displayName(color: TextColor) = Component.translatable(registry().translationKey(), color)

fun Component.noItalic() = decoration(TextDecoration.ITALIC, false)
fun Component.noBold() = decoration(TextDecoration.BOLD, false)
fun Component.withTransition(phase: Float, vararg colors: TextColor) = color(colorTransition(phase, *colors))
fun Component.withGradient(vararg colors: TextColor): Component {
    val split = splitComponentToCharacters(this)
    val size = split.children().size + 1
    var component = Component.empty()
    split.children().forEachIndexed { index, child ->
        val phase = index.toFloat() / size.toFloat()
        component = component.append(child.withTransition(phase, *colors))
    }
    return component
}

infix fun String.withColor(color: TextColor) = Component.text(this, color)
infix fun Component.withColor(color: TextColor) = colorIfAbsent(color)
infix fun Component.withDecoration(decoration: TextDecoration) = decorate(decoration)

fun broadcast(msg: Component) =
    PacketGroupingAudience.of(MinecraftServer.getConnectionManager().onlinePlayers).sendMessage(msg)

fun splitComponentToCharacters(component: Component): Component {
    return buildComponent {
        ComponentFlattener.textOnly().flatten(component) { str ->
            str.forEach { c ->
                +Component.text(c, component.style())
            }
        }
        component.children().forEach { child ->
            +splitComponentToCharacters(child)
        }
    }
}

private fun colorTransition(phase: Float, vararg colors: TextColor): TextColor {
    val steps = 1f / (colors.size - 1)
    var colorIndex = 1
    while (colorIndex < colors.size) {
        val value = colorIndex * steps
        if (value >= phase) {
            val factor = 1 + (phase - value) * (colors.size - 1)
            return TextColor.lerp(factor, colors[colorIndex - 1], colors[colorIndex])
        }
        colorIndex++
    }
    return NamedTextColor.WHITE
}

fun abilityProgressBar(abilityName: String, remainingTime: Int, cooldownTime: Int): Component {
    val bars = 30
    val percentage = remainingTime.toFloat() / cooldownTime.toFloat()

    val uncompletedBars = Component.text("|".repeat((percentage * bars).toInt()), NamedTextColor.YELLOW)
    val completedBars = Component.text("|".repeat((bars - percentage * bars).toInt()), NamedTextColor.GRAY)

    val barsComponent = (uncompletedBars + completedBars).noBold()

    val timeString =
        if (percentage <= 0.05)
            Component.text("READY", NamedTextColor.GREEN)
        else Component.text(String.format("%.1fs", remainingTime.toFloat() / 1000f))
            .withTransition(percentage, NamedTextColor.GREEN, NamedTextColor.YELLOW, NamedTextColor.RED)

    return Component.text(abilityName,
        NamedTextColor.YELLOW,
        TextDecoration.BOLD) + Component.space() + barsComponent + Component.space() + timeString.noBold()
}

class ComponentBuilder {

    private val list = mutableListOf<Component>()

    operator fun Component.unaryPlus() {
        list.add(this)
    }

    operator fun Pair<String, TextColor>.unaryPlus() {
        list.add(Component.text(first, second))
    }

    fun build() = Component.join(JoinConfiguration.noSeparators(), list)
}

inline fun buildComponent(block: ComponentBuilder.() -> Unit) = ComponentBuilder().apply(block).build()