package com.bluedragonmc.server.command

class PingCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        usage(usageString)

        syntax {
            sender.sendMessage(formatMessageTranslated("command.ping.response", player.latency))
        }.requirePlayers()
    })