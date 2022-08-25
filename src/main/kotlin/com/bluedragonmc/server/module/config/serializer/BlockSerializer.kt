package com.bluedragonmc.server.module.config.serializer

import net.minestom.server.instance.block.Block
import org.spongepowered.configurate.serialize.ScalarSerializer
import java.lang.reflect.Type
import java.util.function.Predicate

class BlockSerializer : ScalarSerializer<Block>(Block::class.java) {
    override fun deserialize(type: Type?, obj: Any?): Block? {
        val string = obj.toString()
        return Block.fromNamespaceId(string)
    }

    override fun serialize(item: Block?, typeSupported: Predicate<Class<*>>?): Any? {
        return item?.namespace()?.asString()
    }
}
