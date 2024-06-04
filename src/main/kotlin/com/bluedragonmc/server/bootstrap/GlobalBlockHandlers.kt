package com.bluedragonmc.server.bootstrap

import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.tag.Tag
import net.minestom.server.tag.TagSerializer
import net.minestom.server.utils.NamespaceID

object GlobalBlockHandlers : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        registerHandler(
            "minecraft:sign", listOf(
                // https://minecraft.wiki/w/Sign#Block_data
                Tag.Byte("is_waxed"),
                Tag.NBT("front_text"),
                Tag.NBT("back_text"),
            )
        )
        registerHandler(
            "minecraft:skull", listOf(
                Tag.String("ExtraType"),
                Tag.NBT("SkullOwner")
            )
        )
        registerHandler(
            "minecraft:beacon", listOf(
                Tag.Component("CustomName"),
                Tag.String("Lock"),
                Tag.Integer("Levels"),
                Tag.Integer("Primary"),
                Tag.Integer("Secondary")
            )
        )
        registerHandler(
            "minecraft:furnace", listOf(
                Tag.Short("BurnTime"),
                Tag.Short("CookTime"),
                Tag.Short("CookTimeTotal"),
                Tag.Component("CustomName"),
                Tag.ItemStack("Items").list(),
                Tag.String("Lock"),
                Tag.Integer("RecipesUsed").list()
            )
        )
        registerHandler(
            "minecraft:banner", listOf(
                Tag.String("CustomName"),
                Tag.NBT("Patterns").list()
            )
        )
        registerHandler(
            "minecraft:dropper", listOf(
                Tag.String("CustomName"),
                Tag.ItemStack("Items").list(),
                Tag.String("Lock"),
                Tag.String("LootTable"),
                Tag.Long("LootTableSeed")
            )
        )
        registerHandler("minecraft:daylight_detector", listOf())
        registerHandler(
            "minecraft:chest", listOf(
                Tag.String("CustomName"),
                Tag.ItemStack("Items").list(),
                Tag.String("Lock"),
                Tag.String("LootTable"),
                Tag.Long("LootTableSeed")
            )
        )
    }

    private fun registerHandler(registryName: String, blockEntityTags: List<Tag<*>>) =
        MinecraftServer.getBlockManager().registerHandler(registryName) {
            createHandler(registryName, blockEntityTags)
        }

    private fun createHandler(registryName: String, blockEntityTags: List<Tag<*>>) = object : BlockHandler {
        override fun getNamespaceId() = NamespaceID.from(registryName)
        override fun getBlockEntityTags() = blockEntityTags
    }
}