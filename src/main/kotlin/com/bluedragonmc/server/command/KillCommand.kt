package com.bluedragonmc.server.command

class KillCommand(name: String, usageString: String, vararg aliases: String = emptyArray()) :
    BlueDragonCommand(name, aliases, {
        usage(usageString)

        syntax {
            player.kill()
        }.requirePlayers()

        val playerArgument by PlayerArgument
        syntax(playerArgument) {
            getFirstPlayer(playerArgument).kill()
        }
    })