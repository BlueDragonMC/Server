package com.bluedragonmc.server.utils

import com.bluedragonmc.server.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.flattener.ComponentFlattener
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.translation.GlobalTranslator
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import java.util.*

private val separator
    get() = Component.text("=================================", NamedTextColor.WHITE, TextDecoration.STRIKETHROUGH)

val miniMessage = MiniMessage.builder().editTags { builder ->
    builder.resolvers(
        TagResolver.resolver("p1", Tag.styling(BRAND_COLOR_PRIMARY_1)),
        TagResolver.resolver("p2", Tag.styling(BRAND_COLOR_PRIMARY_2)),
        TagResolver.resolver("p3", Tag.styling(BRAND_COLOR_PRIMARY_3)),
        TagResolver.resolver("a1", Tag.styling(ALT_COLOR_1)),
        TagResolver.resolver("a2", Tag.styling(ALT_COLOR_2)),
        TagResolver.resolver("nobold", Tag.styling(TextDecoration.BOLD.withState(false))),
        TagResolver.resolver("noitalic", Tag.styling(TextDecoration.ITALIC.withState(false)))
    )
}.build()

fun Component?.surroundWithSeparators(): Component {
    if (this == null) return Component.empty()
    return buildComponent {
        +separator
        +Component.newline()
        +decoration(TextDecoration.STRIKETHROUGH, false)
        +Component.newline()
        +separator
    }
}

fun Component.toPlainText() = PlainTextComponentSerializer.plainText().serialize(this)
operator fun Component.plus(component: Component) = append(component)

fun Component.hoverEventTranslatable(key: String, color: TextColor): Component =
    hoverEvent(Component.translatable(key, color))

fun Component.clickEvent(command: String): Component =
    clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, command))

fun Component.clickEvent(action: ClickEvent.Action, value: String): Component {
    if (action == ClickEvent.Action.COPY_TO_CLIPBOARD && hoverEvent() == null) {
        return clickEvent(ClickEvent.clickEvent(action, value)).hoverEventTranslatable(
            "command.click_to_copy",
            ALT_COLOR_1
        )
    }
    if (action == ClickEvent.Action.OPEN_URL && hoverEvent() == null) {
        return clickEvent(ClickEvent.clickEvent(action, value)).hoverEvent(
            Component.translatable(
                "command.click_to_open_url",
                ALT_COLOR_1,
                Component.text(value, NamedTextColor.GRAY)
            )
        )
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

fun splitAndFormatLore(description: Component, color: TextColor, player: Player): List<Component> {
    return splitComponentByNewline(description, player.locale ?: DEFAULT_LOCALE).map {
        it.withColor(color).noItalic()
    }
}

fun hasNewline(component: Component): Boolean =
    component.toPlainText().contains("\n") || component.children().any { hasNewline(it) }

fun splitComponentByNewline(
    component: Component,
    locale: Locale? = null,
    list: MutableList<Component> = mutableListOf(),
): List<Component> {
    if (!hasNewline(component)) {
        return listOf(component) // Components with no newline do not have be rendered and split.
    }
    val rendered = if (locale == null) component else GlobalTranslator.render(component, locale)
    ComponentFlattener.textOnly().flatten(rendered) { str ->
        str.split("\n").forEach { c ->
            list.add(Component.text(c, component.style()))
        }
    }
    component.children().forEach { child ->
        list.addAll(splitComponentByNewline(child))
    }
    return list
}

fun splitComponentToCharacters(component: Component): Component {
    require(component !is TranslatableComponent) { "TranslatableComponent objects can not be split because they are locale-dependent." }
    return buildComponent {
        ComponentFlattener.textOnly().flatten(component) { str ->
            str.forEach { c ->
                +Component.text(c, component.style())
            }
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

fun progressBar(bars: Int, percentage: Float, completedColor: TextColor, incompleteColor: TextColor): Component {
    val uncompletedBars = Component.text("|".repeat((percentage * bars).toInt()), completedColor)
    val completedBars = Component.text("|".repeat(bars - (percentage * bars).toInt()), incompleteColor)

    return (uncompletedBars + completedBars).noBold()
}

fun abilityProgressBar(abilityName: Component, remainingTime: Int, cooldownTime: Int): Component {

    val percentage = remainingTime.toFloat() / cooldownTime.toFloat()
    val barsComponent = progressBar(30, percentage, NamedTextColor.YELLOW, NamedTextColor.GRAY)

    val timeString =
        if (percentage <= 0.05)
            Component.translatable("global.ability.ready", NamedTextColor.GREEN)
        else Component.translatable(
            "global.ability.cooldown_time",
            Component.text(String.format("%.1f", remainingTime.toFloat() / 1000f))
        )
            .withTransition(percentage, NamedTextColor.GREEN, NamedTextColor.YELLOW, NamedTextColor.RED)

    return abilityName + Component.space() + barsComponent + Component.space() + timeString.noBold()
}

class ComponentBuilder {

    private val list = mutableListOf<Component>()

    operator fun Component.unaryPlus() {
        list.add(this)
    }

    operator fun Pair<String, TextColor>.unaryPlus() {
        list.add(Component.translatable(first, second))
    }

    fun build() = Component.join(JoinConfiguration.noSeparators(), list)
}

inline fun buildComponent(block: ComponentBuilder.() -> Unit) = ComponentBuilder().apply(block).build()