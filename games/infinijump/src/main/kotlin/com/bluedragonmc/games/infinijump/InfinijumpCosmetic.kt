package com.bluedragonmc.games.infinijump

import com.bluedragonmc.server.module.database.CosmeticsModule
import net.minestom.server.instance.block.Block

enum class InfinijumpCosmetic(override val id: String, val blockType: () -> Block) : CosmeticsModule.Cosmetic {

    RED("infinijump_blocks_red", Block.RED_CONCRETE),
    GREEN("infinijump_blocks_green", Block.LIME_CONCRETE),
    BLUE("infinijump_blocks_blue", Block.BLUE_CONCRETE),
    RAINBOW_WOOL("infinijump_blocks_rainbow_wool", {
        listOf(
            Block.WHITE_WOOL,
            Block.ORANGE_WOOL,
            Block.MAGENTA_WOOL,
            Block.LIGHT_BLUE_WOOL,
            Block.YELLOW_WOOL,
            Block.LIME_WOOL,
            Block.PINK_WOOL,
            Block.GRAY_WOOL,
            Block.LIGHT_GRAY_WOOL,
            Block.CYAN_WOOL,
            Block.PURPLE_WOOL,
            Block.BLUE_WOOL,
            Block.BROWN_WOOL,
            Block.GREEN_WOOL,
            Block.RED_WOOL,
            Block.BLACK_WOOL
        ).random()
    }),
    RAINBOW_CONCRETE("infinijump_blocks_rainbow_concrete", {
        listOf(
            Block.WHITE_CONCRETE,
            Block.ORANGE_CONCRETE,
            Block.MAGENTA_CONCRETE,
            Block.LIGHT_BLUE_CONCRETE,
            Block.YELLOW_CONCRETE,
            Block.LIME_CONCRETE,
            Block.PINK_CONCRETE,
            Block.GRAY_CONCRETE,
            Block.LIGHT_GRAY_CONCRETE,
            Block.CYAN_CONCRETE,
            Block.PURPLE_CONCRETE,
            Block.BLUE_CONCRETE,
            Block.BROWN_CONCRETE,
            Block.GREEN_CONCRETE,
            Block.RED_CONCRETE,
            Block.BLACK_CONCRETE
        ).random()
    }),
    JUNGLE("infinijump_blocks_jungle", {
        listOf(
            Block.OAK_LEAVES,
            Block.SPRUCE_LEAVES,
            Block.JUNGLE_LEAVES,
            Block.ACACIA_LEAVES,
            Block.DARK_OAK_LEAVES,
            Block.AZALEA_LEAVES,
            Block.FLOWERING_AZALEA_LEAVES,
            Block.OAK_WOOD,
            Block.SPRUCE_WOOD,
            Block.JUNGLE_WOOD,
            Block.ACACIA_WOOD,
            Block.DARK_OAK_WOOD,
            Block.OAK_LOG,
            Block.SPRUCE_LOG,
            Block.JUNGLE_LOG,
            Block.ACACIA_LOG,
            Block.DARK_OAK_LOG,
        ).random()
    });

    constructor(id: String, blockType: Block) : this(id, { blockType })
}
