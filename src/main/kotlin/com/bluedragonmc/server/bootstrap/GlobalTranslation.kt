package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.NAMESPACE
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.GlobalTranslator.renderer
import net.kyori.adventure.translation.TranslationRegistry
import net.minestom.server.adventure.MinestomAdventure
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.util.*

/**
 * Automatically translates text components server-side
 * using Adventure's GlobalTranslator API.
 * Translation files are added by creating an entry in the `i18n.properties` file
 * and creating a new file with the same name as the added property value.
 * The default locale is [Locale.ENGLISH], so non-existing translations fall back to English.
 *
 * **Note**: This currently does not work with grouped packets as they are only serialized once.
 * Also, many packets, like [net.minestom.server.network.packet.server.play.ActionBarPacket], do not support translation
 * because they don't implement [net.minestom.server.network.packet.server.ComponentHoldingServerPacket].
 */
object GlobalTranslation : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        MinestomAdventure.AUTOMATIC_COMPONENT_TRANSLATION = true

        val classLoader = GlobalTranslation::class.java.classLoader
        val registry = TranslationRegistry.create(Key.key(NAMESPACE, "i18n"))
        val translations = Properties().apply {
            load(classLoader.getResourceAsStream("i18n.properties"))
        }
        val languages = translations.keys.map { it.toString() }.filter { it.startsWith("lang_") }
        for (language in languages) {
            val path = translations.getProperty(language)
            val locale = Locale.forLanguageTag(language.substringAfter("lang_"))
            val bundle = PropertyResourceBundle(classLoader.getResourceAsStream(path))
            registry.registerAll(locale, bundle, true)
            logger.info("Registered language $language (locale: $locale) from file ${translations.getProperty(language)}")
        }
        registry.defaultLocale(MinestomAdventure.getDefaultLocale())

        GlobalTranslator.translator().addSource(registry)
    }
}

fun GlobalTranslator.render(component: Component, player: Player) = renderer().render(component, player.locale ?: Locale.ENGLISH)