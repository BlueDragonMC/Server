package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.DEFAULT_LOCALE
import com.bluedragonmc.server.NAMESPACE
import com.bluedragonmc.server.queue.GameClassLoader
import net.kyori.adventure.key.Key
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.TranslationRegistry
import net.minestom.server.adventure.MinestomAdventure
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.net.URL
import java.util.*

/**
 * Automatically translates text components server-side
 * using Adventure's GlobalTranslator API. Translation
 * files are added by creating an entry in the `i18n.properties`
 * file and creating a new file with the same name as the
 * added property value. The default locale is [Locale.ENGLISH],
 * so non-existing translations in other languages fall back to
 * English.
 */
object GlobalTranslation : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        MinestomAdventure.AUTOMATIC_COMPONENT_TRANSLATION = true
        addSource(NAMESPACE, GlobalTranslation::class.java.classLoader)
    }

    /**
     * Adds a source to the [GlobalTranslator] by looking for
     * a `i18n.properties` file using the supplied [classLoader].
     * `i18n.properties` should contain key formatted like
     * `lang_${lang}`, where `${lang}` is the language code, like `en-US`.
     * Language codes must work with [Locale.forLanguageTag].
     *
     * Then, values should be file names of `.properties` files that
     * contain translations for the key's language.
     *
     * For example:
     * ```properties
     * lang_en-US=lang_en.properties
     * lang_en-PT=lang_en_pt.properties
     * lang_zh-CN=lang_zh_cn.properties
     * ```
     * Each `.properties` file specified as a value should
     * contain translation keys. It will be loaded
     * using the same [classLoader].
     */
    fun addSource(namespace: String, classLoader: ClassLoader) {
        val registry = TranslationRegistry.create(Key.key(namespace, "i18n"))
        val translations = Properties()
        val file = getEntryAsStream(classLoader, "i18n.properties")
        if (file == null) {
            logger.warn("i18n.properties file not found in namespace '$namespace'.")
            return
        }
        translations.load(file)
        val languages = translations.keys.map { it.toString() }.filter { it.startsWith("lang_") }
        if (languages.isEmpty()) {
            logger.warn("Could not find any translations in namespace '$namespace'.")
        }
        for (language in languages) {
            val path = translations.getProperty(language)
            val stream = getEntryAsStream(classLoader, path)
            if (stream == null) {
                logger.warn("Failed to find translation file '$path' in namespace '$namespace'.")
                continue
            }
            val locale = Locale.forLanguageTag(language.substringAfter("lang_"))
            val bundle = PropertyResourceBundle(stream)
            registry.registerAll(locale, bundle, true)
            logger.debug("Registered language $language (locale: $locale) from file ${translations.getProperty(language)}")
        }
        registry.defaultLocale(DEFAULT_LOCALE)

        GlobalTranslator.translator().addSource(registry)
    }

    private fun getEntryAsStream(classLoader: ClassLoader, name: String) = getEntry(classLoader, name)?.openStream()

    private fun getEntry(classLoader: ClassLoader, name: String): URL? =
        if (classLoader is GameClassLoader) {
            classLoader.findResource(name)
        } else {
            classLoader.getResource(name)
        }

}
