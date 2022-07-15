package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.utils.buildComponent
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
        player.sendMessage(buildComponent {
            // "Set block at position (x, y, z) to Hay Bale."
            + ("Set block at position " to BRAND_COLOR_PRIMARY_2)
            + ("(${pos.x}, ${pos.y}, ${pos.z})" to BRAND_COLOR_PRIMARY_1)
            + (" to " to BRAND_COLOR_PRIMARY_2)
            + Component.translatable(block.registry().translationKey(), BRAND_COLOR_PRIMARY_1)
        })
    }
})