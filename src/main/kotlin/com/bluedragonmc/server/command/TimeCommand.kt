package com.bluedragonmc.server.command

import net.minestom.server.command.builder.arguments.ArgumentWord

class TimeCommand(name: String, usageString: String, vararg aliases: String?) :
    BlueDragonCommand(name, aliases, block = {
        val timeArgument by IntArgument
        val timeRateArgument by IntArgument
        val timePresetArgument = ArgumentWord("time").from("day", "night", "noon", "midnight", "sunrise", "sunset")

        usage(usageString)

        syntax {
            player.sendMessage(formatMessageTranslated("commands.time.query", player.instance!!.time))
        }

        subcommand("add") {
            usage("/time add <time>")
            syntax(timeArgument) {
                player.instance!!.time += get(timeArgument)
                player.sendMessage(formatMessageTranslated("commands.time.set", player.instance!!.time))
            }
        }

        subcommand("query") {
            usage("/time query")
            syntax {
                player.sendMessage(formatMessageTranslated("commands.time.query", player.instance!!.time))
            }
        }

        subcommand("set") {
            usage("/time set <time>")
            syntax(timeArgument) {
                val newTime = get(timeArgument)
                player.instance!!.time = newTime.toLong()
                player.sendMessage(formatMessageTranslated("commands.time.set", newTime))
            }
            syntax(timePresetArgument) {
                val newTime = when(get(timePresetArgument)) {
                    "day" -> 1000L
                    "night" -> 13000L
                    "noon" -> 6000L
                    "midnight" -> 18000L
                    "sunrise" -> 23000L
                    "sunset" -> 12000L
                    else -> 6000L
                }
                player.instance!!.time = newTime
                player.sendMessage(formatMessageTranslated("commands.time.set", newTime))
            }
        }

        subcommand("rate") {
            usage("/time rate <query|set> ...")
            syntax {
                player.sendMessage(formatMessageTranslated("command.time.rate.query", player.instance!!.timeRate))
            }
            subcommand("query") {
                usage("/time rate query")
                syntax {
                    player.sendMessage(formatMessageTranslated("command.time.rate.query", player.instance!!.timeRate))
                }
            }
            subcommand("set") {
                usage("/time rate set <newRate>")
                syntax(timeRateArgument) {
                    val newRate = get(timeRateArgument)
                    player.instance!!.timeRate = newRate
                    player.sendMessage(formatMessageTranslated("command.time.rate.set", newRate))
                }
            }
        }
    })