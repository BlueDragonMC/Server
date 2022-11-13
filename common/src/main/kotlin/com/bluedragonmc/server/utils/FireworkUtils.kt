package com.bluedragonmc.server.utils

import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.FireworkRocketMeta
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.EntityStatusPacket
import net.minestom.server.sound.SoundEvent
import java.time.Duration

object FireworkUtils {

    fun spawnFirework(instance: Instance, position: Pos, millisBeforeDetonate: Long, fireworkMeta: ItemMeta) {
        val fireworkItem = ItemStack.builder(Material.FIREWORK_ROCKET).meta(fireworkMeta).build()
        val firework = Entity(EntityType.FIREWORK_ROCKET)
        (firework.entityMeta as FireworkRocketMeta).fireworkInfo = fireworkItem
        firework.setNoGravity(true)
        firework.setInstance(instance, position)
        instance.playSound(
            Sound.sound(
                SoundEvent.ENTITY_FIREWORK_ROCKET_LAUNCH, Sound.Source.PLAYER, 2.0f, 1.0f
            ), position
        )
        MinecraftServer.getSchedulerManager().buildTask {
            instance.sendGroupedPacket(EntityStatusPacket(firework.entityId, 17))
            firework.remove()
        }.delay(Duration.ofMillis(millisBeforeDetonate)).schedule()
    }
}