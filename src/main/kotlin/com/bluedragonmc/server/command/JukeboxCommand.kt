package com.bluedragonmc.server.command

import com.bluedragonmc.api.grpc.JukeboxOuterClass
import com.bluedragonmc.api.grpc.copy
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.bootstrap.Jukebox
import com.bluedragonmc.server.bootstrap.Jukebox.currentTimestamp
import com.bluedragonmc.server.bootstrap.Jukebox.toMillis
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.ServerFlag

class JukeboxCommand(name: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        requirePlayers()

        syntax {
            Jukebox.songSelectMenu.open(player)
        }

        subcommand("play") {
            syntax {
                Jukebox.songSelectMenu.open(player)
            }
        }

        subcommand("pause") {
            suspendSyntax {
                val current = Messaging.outgoing.getSongInfo(player)
                if (current.isPlaying) {
                    Messaging.outgoing.setSongInfo(player, current.copy {
                        isPlaying = false
                        val millisSinceStart = (System.currentTimeMillis() - startedPlayingAt.toMillis()).toInt()
                        val ticksSinceStart = millisSinceStart / (1000 / ServerFlag.SERVER_TICKS_PER_SECOND)
                        startingTick += ticksSinceStart
                    })
                }
            }
        }

        subcommand("unpause") {
            suspendSyntax {
                val current = Messaging.outgoing.getSongInfo(player)
                if (!current.isPlaying) {
                    Messaging.outgoing.setSongInfo(player, current.copy {
                        isPlaying = true
                        startedPlayingAt = currentTimestamp()
                    })
                }
            }
        }

        subcommand("stop") {
            suspendSyntax {
                Jukebox.stop(player)
            }
        }

        subcommand("clear") {
            suspendSyntax {
                Messaging.outgoing.setSongInfo(player, JukeboxOuterClass.PlayerSongQueue.getDefaultInstance())
            }
        }

        subcommand("skip") {
            suspendSyntax {
                Jukebox.stop(player)
                Jukebox.restartCurrentSong(player)
            }
        }

        subcommand("remove") {
            val trackNumberArgument by IntArgument
            suspendSyntax(trackNumberArgument) {
                val index = get(trackNumberArgument) - 1 // subtract 1 to convert into an index
                val current = Messaging.outgoing.getSongInfo(player)
                if (index >= current.songsList.size) return@suspendSyntax
                val newSongs = current.songsList.toMutableList()
                newSongs.removeAt(index)
                Messaging.outgoing.setSongInfo(player, current.copy {
                    songs.clear()
                    songs.addAll(newSongs)
                })
            }
        }

        subcommand("queue") {
            suspendSyntax {
                val queue = Messaging.outgoing.getSongInfo(player)
                val status = Jukebox.getState(player)
                val msg = Component.text()
                for ((index, song) in queue.songsList.withIndex()) {
                    msg.append(
                        Component.text("${index + 1}. ", NamedTextColor.GRAY)
                            .append(Component.text(song.songName, BRAND_COLOR_PRIMARY_1))
                    )
                    if (index == 0) {
                        // First song should have play status next to it
                        val pauseStatus = if (status == null) "⏸" else "▶"
                        val tick = status?.tick ?: queue.startingTick
                        val secondsElapsed: Int = (tick / song.tempo).toInt()
                        val totalSeconds: Int = (song.songLengthTicks / song.tempo).toInt()
                        val timeElapsedStr =
                            "${secondsElapsed / 60}:${(secondsElapsed % 60).toString().padStart(2, '0')}"
                        val timeTotalStr = "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
                        msg.append(Component.text(" $pauseStatus $timeElapsedStr/$timeTotalStr", NamedTextColor.BLUE))
                    }
                    if (index < queue.songsList.size - 1) msg.append(Component.newline())
                }
                if (queue.songsList.isEmpty()) {
                    msg.append(Component.text("Your song queue is empty!", NamedTextColor.RED))
                    msg.append(Component.newline())
                    msg.append(
                        Component.text("Type ", NamedTextColor.GRAY)
                            .append(Component.text("/play", BRAND_COLOR_PRIMARY_1))
                            .append(Component.text(" to play music.", NamedTextColor.GRAY))
                    )
                }
                player.sendMessage(msg.build().surroundWithSeparators())
            }
        }
    })
