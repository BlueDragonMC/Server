package com.bluedragonmc.server.block

import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.tag.Tag
import net.minestom.server.utils.NamespaceID

class SkullHandler : BlockHandler {
    override fun getBlockEntityTags() = listOf(
        Tag.String("ExtraType"),
        Tag.NBT("SkullOwner")
    )

    override fun getNamespaceId() = NamespaceID.from("minecraft:skull")
}