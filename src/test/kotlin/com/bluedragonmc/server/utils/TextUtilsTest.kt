package com.bluedragonmc.server.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TextUtilsTest {

    private val components = listOf(
        Component.text("Hello, world!"),
        Component.text("Component with formatting", NamedTextColor.BLUE),
        Component.text("Component with sibling").append(Component.text(" - sibling!")),
        Component.empty().append(Component.text("(child 1)")).append(Component.text("(child 2)"))
    )

    @Test
    fun splitToCharacters() {
        val results = components.map {
            splitComponentToCharacters(it)
        }

        components.zip(results).forEach { (original, split) ->
            assertEquals(original.toPlainText(), split.toPlainText())
        }
    }

    @Test
    fun `Split translatable component throws`() {
        assertThrows<IllegalArgumentException> {
            splitComponentToCharacters(Component.translatable("tile.stone.name"))
        }
    }

    @Test
    fun testComponentBuilder() {
        val result = buildComponent {
            components.forEach { +it }
        }
        val expected = Component.join(JoinConfiguration.noSeparators(), components)

        assertEquals(result, expected)
    }

    @Test
    fun toPlainText() {
        val component = Component.text("Hello, world!")
        val plainText = component.toPlainText()

        assertEquals("Hello, world!", plainText)
    }

}