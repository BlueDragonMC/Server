package com.bluedragonmc.server.utils

import net.kyori.adventure.sound.Sound
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent

object SoundUtils {
    fun playSoundInWorld(sound: Sound, instance: Instance, position: Point) {
        val players = mutableListOf<Player>()
        instance.entityTracker.nearbyEntities(position, 16.0, EntityTracker.Target.PLAYERS, players::add)
        PacketGroupingAudience.of(players).playSound(sound, position.x(), position.y(), position.z())
    }

    val hurtSounds = mapOf(
        EntityType.ALLAY to SoundEvent.ENTITY_ALLAY_HURT,
        EntityType.AXOLOTL to SoundEvent.ENTITY_AXOLOTL_HURT,
        EntityType.BAT to SoundEvent.ENTITY_BAT_HURT,
        EntityType.BEE to SoundEvent.ENTITY_BEE_HURT,
        EntityType.BLAZE to SoundEvent.ENTITY_BLAZE_HURT,
        EntityType.CAMEL to SoundEvent.ENTITY_CAMEL_HURT,
        EntityType.CAT to SoundEvent.ENTITY_CAT_HURT,
        EntityType.CAVE_SPIDER to SoundEvent.ENTITY_SPIDER_HURT,
        EntityType.CHICKEN to SoundEvent.ENTITY_CHICKEN_HURT,
        EntityType.COD to SoundEvent.ENTITY_COD_HURT,
        EntityType.COW to SoundEvent.ENTITY_COW_HURT,
        EntityType.CREEPER to SoundEvent.ENTITY_CREEPER_HURT,
        EntityType.DOLPHIN to SoundEvent.ENTITY_DOLPHIN_HURT,
        EntityType.DONKEY to SoundEvent.ENTITY_DONKEY_HURT,
        EntityType.DROWNED to SoundEvent.ENTITY_DROWNED_HURT,
        EntityType.ELDER_GUARDIAN to SoundEvent.ENTITY_ELDER_GUARDIAN_HURT,
        EntityType.ENDER_DRAGON to SoundEvent.ENTITY_ENDER_DRAGON_HURT,
        EntityType.ENDERMAN to SoundEvent.ENTITY_ENDERMAN_HURT,
        EntityType.ENDERMITE to SoundEvent.ENTITY_ENDERMITE_HURT,
        EntityType.EVOKER to SoundEvent.ENTITY_EVOKER_HURT,
        EntityType.FOX to SoundEvent.ENTITY_FOX_HURT,
        EntityType.FROG to SoundEvent.ENTITY_FROG_HURT,
        EntityType.GHAST to SoundEvent.ENTITY_GHAST_HURT,
        EntityType.GLOW_SQUID to SoundEvent.ENTITY_GLOW_SQUID_HURT,
        EntityType.GOAT to SoundEvent.ENTITY_GOAT_HURT,
        EntityType.GUARDIAN to SoundEvent.ENTITY_GUARDIAN_HURT,
        EntityType.HOGLIN to SoundEvent.ENTITY_HOGLIN_HURT,
        EntityType.HORSE to SoundEvent.ENTITY_HORSE_HURT,
        EntityType.HUSK to SoundEvent.ENTITY_HUSK_HURT,
        EntityType.ILLUSIONER to SoundEvent.ENTITY_ILLUSIONER_HURT,
        EntityType.IRON_GOLEM to SoundEvent.ENTITY_IRON_GOLEM_HURT,
        EntityType.LLAMA to SoundEvent.ENTITY_LLAMA_HURT,
        EntityType.MAGMA_CUBE to SoundEvent.ENTITY_MAGMA_CUBE_HURT,
        EntityType.MOOSHROOM to SoundEvent.ENTITY_COW_HURT,
        EntityType.MULE to SoundEvent.ENTITY_MULE_HURT,
        EntityType.OCELOT to SoundEvent.ENTITY_OCELOT_HURT,
        EntityType.PANDA to SoundEvent.ENTITY_PANDA_HURT,
        EntityType.PARROT to SoundEvent.ENTITY_PARROT_HURT,
        EntityType.PHANTOM to SoundEvent.ENTITY_PHANTOM_HURT,
        EntityType.PIG to SoundEvent.ENTITY_PIG_HURT,
        EntityType.PIGLIN to SoundEvent.ENTITY_PIGLIN_HURT,
        EntityType.PIGLIN_BRUTE to SoundEvent.ENTITY_PIGLIN_BRUTE_HURT,
        EntityType.PILLAGER to SoundEvent.ENTITY_PILLAGER_HURT,
        EntityType.PLAYER to SoundEvent.ENTITY_PLAYER_HURT,
        EntityType.POLAR_BEAR to SoundEvent.ENTITY_POLAR_BEAR_HURT,
        EntityType.PUFFERFISH to SoundEvent.ENTITY_PUFFER_FISH_HURT,
        EntityType.RABBIT to SoundEvent.ENTITY_RABBIT_HURT,
        EntityType.RAVAGER to SoundEvent.ENTITY_RAVAGER_HURT,
        EntityType.SALMON to SoundEvent.ENTITY_SALMON_HURT,
        EntityType.SHEEP to SoundEvent.ENTITY_SHEEP_HURT,
        EntityType.SHULKER to SoundEvent.ENTITY_SHULKER_HURT,
        EntityType.SILVERFISH to SoundEvent.ENTITY_SILVERFISH_HURT,
        EntityType.SKELETON to SoundEvent.ENTITY_SKELETON_HURT,
        EntityType.SKELETON_HORSE to SoundEvent.ENTITY_SKELETON_HORSE_HURT,
        EntityType.SLIME to SoundEvent.ENTITY_SLIME_HURT,
        EntityType.SNIFFER to SoundEvent.ENTITY_SNIFFER_HURT,
        EntityType.SNOW_GOLEM to SoundEvent.ENTITY_SNOW_GOLEM_HURT,
        EntityType.SPIDER to SoundEvent.ENTITY_SPIDER_HURT,
        EntityType.SQUID to SoundEvent.ENTITY_SQUID_HURT,
        EntityType.STRAY to SoundEvent.ENTITY_STRAY_HURT,
        EntityType.STRIDER to SoundEvent.ENTITY_STRIDER_HURT,
        EntityType.TADPOLE to SoundEvent.ENTITY_TADPOLE_HURT,
        EntityType.TRADER_LLAMA to SoundEvent.ENTITY_LLAMA_HURT,
        EntityType.TROPICAL_FISH to SoundEvent.ENTITY_TROPICAL_FISH_HURT,
        EntityType.TURTLE to SoundEvent.ENTITY_TURTLE_HURT
    )
}