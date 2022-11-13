package com.bluedragonmc.server

import com.bluedragonmc.server.bootstrap.GlobalTranslation
import com.bluedragonmc.server.utils.toPlainText
import net.kyori.adventure.text.Component
import net.kyori.adventure.translation.GlobalTranslator
import net.minestom.server.event.EventNode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.*
import kotlin.test.assertEquals

class GlobalTranslatorTest {

    @Test
    fun `Load translations`() {
        assertDoesNotThrow {
            GlobalTranslation.hook(EventNode.all(""))
        }
    }

    @Test
    fun `String translation`() {
        val key = Component.translatable("global.server.domain")
        val string = "bluedragonmc.com"
        assertEquals(GlobalTranslator.render(key, Locale.ENGLISH).toPlainText(), string)
    }

}