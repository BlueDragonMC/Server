package com.bluedragonmc.server.utils

import net.kyori.adventure.sound.Sound
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance

object SoundUtils {
    fun playSoundInWorld(sound: Sound, instance: Instance, position: Point) {
        val players = mutableListOf<Player>()
        instance.entityTracker.nearbyEntities(position, 16.0, EntityTracker.Target.PLAYERS, players::add)
        PacketGroupingAudience.of(players).playSound(sound, position.x(), position.y(), position.z())
    }
}