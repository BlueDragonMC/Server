package com.bluedragonmc.server.command

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.command.builder.arguments.ArgumentWord
import net.minestom.server.command.builder.arguments.number.ArgumentFloat
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.sound.SoundEvent

class PlaysoundCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        val soundArgument = ArgumentWord("sound").from(*SoundEvent.values().map { it.name() }.toTypedArray())
        val sourceArgument = ArgumentWord("source").from(*Sound.Source.values().map { it.name.lowercase() }.toTypedArray())
        val targetArgument = ArgumentPlayer("target")
        val positionArgument by BlockPosArgument
        val volumeArgument = ArgumentFloat("volume").setDefaultValue(1.0f)
        val pitchArgument = ArgumentFloat("pitch").setDefaultValue(1.0f)

        usage(usageString)

        syntax(soundArgument, sourceArgument, targetArgument, positionArgument, volumeArgument, pitchArgument) {
            val targets = get(targetArgument).find(sender).filterIsInstance<Player>()
            val position = get(positionArgument).from((sender as? Player)?.position ?: Pos(0.0, 0.0, 0.0))
            val audience = PacketGroupingAudience.of(targets)
            audience.playSound(
                Sound.sound(
                    Key.key(get(soundArgument)),
                    Sound.Source.valueOf(get(sourceArgument).uppercase()),
                    get(volumeArgument),
                    get(pitchArgument)
                ),
                position.x,
                position.y,
                position.z
            )
            if (targets.size == 1)
                sender.sendMessage(formatMessageTranslated("commands.playsound.success.single", get(soundArgument), targets.first().name))
            else
                sender.sendMessage(formatMessageTranslated("commands.playsound.success.multiple", get(soundArgument), targets.size))
        }

        syntax(soundArgument, sourceArgument, targetArgument, volumeArgument, pitchArgument) {
            val targets = get(targetArgument).find(sender).filterIsInstance<Player>()
            val audience = PacketGroupingAudience.of(targets)
            audience.playSound(
                Sound.sound(
                    Key.key(get(soundArgument)),
                    Sound.Source.valueOf(get(sourceArgument).uppercase()),
                    get(volumeArgument),
                    get(pitchArgument)
                )
            )
            if (targets.size == 1)
                sender.sendMessage(formatMessageTranslated("commands.playsound.success.single", get(soundArgument), targets.first().name))
            else
                sender.sendMessage(formatMessageTranslated("commands.playsound.success.multiple", get(soundArgument), targets.size))
        }

    })