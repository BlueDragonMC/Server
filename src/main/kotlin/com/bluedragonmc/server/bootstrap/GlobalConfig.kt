package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.module.config.serializer.ComponentSerializer
import net.kyori.adventure.text.Component
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader

object GlobalConfig : Bootstrap() {

    lateinit var config: ConfigurationNode

    override fun hook(eventNode: EventNode<Event>) {
        val reader =
            BufferedReader(InputStreamReader(javaClass.classLoader.getResourceAsStream("config/global.yml")!!))
        val loader = YamlConfigurationLoader.builder()
            .source { reader }
            .build()

        config = loader.load(ConfigurationOptions.defaults().serializers { builder ->
            builder.register(Component::class.java, ComponentSerializer())
        })
    }
}