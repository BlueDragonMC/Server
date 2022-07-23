package com.bluedragonmc.server.command

import net.kyori.adventure.text.Component

class GiveCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {
    usage(usageString)

    val itemArgument by ItemStackArgument
    val playerArgument by PlayerArgument
    val amountArgument by IntArgument

    syntax(itemArgument) {
        val itemStack = get(itemArgument)

        player.inventory.addItemStack(itemStack)

        sender.sendMessage(
            formatMessage(
                "{} were given {}x {}.",
                "You",
                1,
                Component.translatable(itemStack.material().registry().translationKey())
            )
        )
    }.requirePlayers()

    syntax(itemArgument, amountArgument) {
        val itemStack = get(itemArgument)
        val amount = get(amountArgument)

        player.inventory.addItemStack(itemStack.withAmount(amount))

        sender.sendMessage(
            formatMessage(
                "{} were given {}x {}.",
                "You",
                amount,
                Component.translatable(itemStack.material().registry().translationKey())
            )
        )
    }.requirePlayers()

    syntax(playerArgument, itemArgument) {
        val player = getFirstPlayer(playerArgument)
        val itemStack = get(itemArgument)

        player.inventory.addItemStack(itemStack)

        sender.sendMessage(
            formatMessage(
                "{} was given {}x {}.",
                player.name,
                1,
                Component.translatable(itemStack.material().registry().translationKey())
            )
        )
    }

    syntax(playerArgument, itemArgument, amountArgument) {
        val player = getFirstPlayer(playerArgument)
        val itemStack = get(itemArgument)
        val amount = get(amountArgument)

        player.inventory.addItemStack(itemStack.withAmount(amount))

        sender.sendMessage(
            formatMessage(
                "{} was given {}x {}.",
                player.name,
                amount,
                Component.translatable(itemStack.material().registry().translationKey())
            )
        )
    }
})