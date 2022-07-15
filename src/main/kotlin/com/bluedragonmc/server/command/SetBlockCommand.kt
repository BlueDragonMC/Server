package com.bluedragonmc.server.command

import net.kyori.adventure.text.Component

class SetBlockCommand(
    name: String,
    usageString: String,
    vararg aliases: String = emptyArray(),
) : BlueDragonCommand(name, aliases, {

    usage(usageString)

    val positionArgument by BlockPosArgument
    val blockArgument by BlockStateArgument

    syntax(positionArgument, blockArgument) {
        val pos = get(positionArgument).from(player)
        val block = get(blockArgument)

        player.instance?.setBlock(pos, block)
        player.sendMessage(
            formatMessage(
                "Set block at position {} to {}",
                "(${pos.x}, ${pos.y}, ${pos.z})",
                Component.translatable(block.registry().translationKey())
            )
        )
    }.requirePlayers()
})