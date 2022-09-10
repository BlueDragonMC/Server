package com.bluedragonmc.server.module.config.serializer

import net.minestom.server.entity.PlayerSkin
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

class PlayerSkinSerializer : TypeSerializer<PlayerSkin> {
    override fun deserialize(type: Type?, node: ConfigurationNode?): PlayerSkin {
        val textures = node?.node("textures")?.string
        val signature = node?.node("signature")?.string
        return PlayerSkin(textures, signature)
    }

    override fun serialize(type: Type?, obj: PlayerSkin?, node: ConfigurationNode?) {
        node?.node("textures")?.set(obj?.textures())
        node?.node("signature")?.set(obj?.signature())
    }
}