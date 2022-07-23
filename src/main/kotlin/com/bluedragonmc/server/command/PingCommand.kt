package com.bluedragonmc.server.command

class PingCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        usage(usageString)

        syntax {
            sender.sendMessage(formatMessage("Your ping is: ${player.latency}ms"))
        }.requirePlayers()
    })