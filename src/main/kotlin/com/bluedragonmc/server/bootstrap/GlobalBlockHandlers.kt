package com.bluedragonmc.server.bootstrap

import net.kyori.adventure.key.Key
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.tag.Tag

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
                Tag.NBT("profile")
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
                Tag.Short("lit_time_remaining"),
                Tag.Short("cooking_time_spent"),
                Tag.Short("cooking_total_time"),
                Tag.Short("lit_total_time"),
                Tag.Component("CustomName"),
                Tag.ItemStack("Items").list(),
                Tag.String("Lock"),
                Tag.Integer("RecipesUsed").list()
            )
        )
        registerHandler(
            "minecraft:banner", listOf(
                Tag.String("CustomName"),
                Tag.NBT("patterns").list()
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
        registerHandler(
            "minecraft:shelf", listOf(
                Tag.NBT("Items"),
                Tag.Boolean("align_items_to_bottom")
            )
        )
    }

    private fun registerHandler(registryName: String, blockEntityTags: List<Tag<*>>) =
        MinecraftServer.getBlockManager().registerHandler(registryName) {
            createHandler(registryName, blockEntityTags)
        }

    private fun createHandler(registryName: String, blockEntityTags: List<Tag<*>>) = object : BlockHandler {
        override fun getKey() = Key.key(registryName)
        override fun getBlockEntityTags() = blockEntityTags
    }
}