package com.bluedragonmc.server.block

import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.tag.Tag
import net.minestom.server.utils.NamespaceID

class SignHandler : BlockHandler {
    override fun getBlockEntityTags() = listOf(
        Tag.Byte("GlowingText"),
        Tag.String("Color"),
        Tag.String("Text1"),
        Tag.String("Text2"),
        Tag.String("Text3"),
        Tag.String("Text4"),
    )

    override fun getNamespaceId() = NamespaceID.from("minecraft:sign")
}