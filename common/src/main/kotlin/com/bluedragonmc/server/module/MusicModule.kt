package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.service.Messaging
import kotlinx.coroutines.launch
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

class MusicModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {

    }

    fun play(player: Player, songName: String, queuePosition: Int, startTimeInTicks: Int, tags: List<String>) {
        Messaging.IO.launch {
            Messaging.outgoing.playSong(player, songName, queuePosition, startTimeInTicks, tags)
        }
    }
}