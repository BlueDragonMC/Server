package com.bluedragonmc.server.command

import net.kyori.adventure.text.Component

class GiveCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        usage(usageString)

        val itemArgument by ItemStackArgument
        val playerArgument by PlayerArgument
        val amountArgument by IntArgument

        syntax(itemArgument) {
            val itemStack = get(itemArgument)

            player.inventory.addItemStack(itemStack)

            sender.sendMessage(formatMessageTranslated("command.give.self",
                1,
                Component.translatable(itemStack.material().registry().translationKey())))
        }.requirePlayers()

        syntax(itemArgument, amountArgument) {
            val itemStack = get(itemArgument)
            val amount = get(amountArgument)

            player.inventory.addItemStack(itemStack.withAmount(amount))

            sender.sendMessage(formatMessageTranslated("command.give.self",
                amount,
                Component.translatable(itemStack.material().registry().translationKey())))
        }.requirePlayers()

        syntax(playerArgument, itemArgument) {
            val player = getFirstPlayer(playerArgument)
            val itemStack = get(itemArgument)

            player.inventory.addItemStack(itemStack)

            sender.sendMessage(formatMessageTranslated("command.give.other",
                player.name,
                1,
                Component.translatable(itemStack.material().registry().translationKey())))
        }

        syntax(playerArgument, itemArgument, amountArgument) {
            val player = getFirstPlayer(playerArgument)
            val itemStack = get(itemArgument)
            val amount = get(amountArgument)

            player.inventory.addItemStack(itemStack.withAmount(amount))

            sender.sendMessage(formatMessageTranslated("command.give.other",
                player.name,
                amount,
                Component.translatable(itemStack.material().registry().translationKey())))
        }
    })