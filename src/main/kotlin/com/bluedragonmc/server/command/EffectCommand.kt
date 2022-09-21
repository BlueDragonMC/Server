package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import net.kyori.adventure.text.Component
import net.minestom.server.command.builder.arguments.minecraft.registry.ArgumentPotionEffect
import net.minestom.server.command.builder.arguments.number.ArgumentInteger
import net.minestom.server.entity.Player
import net.minestom.server.potion.Potion
import kotlin.experimental.or

class EffectCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        val targetArgument = ArgumentPlayer("target")
        val effectArgument = ArgumentPotionEffect("effect")
        val secondsArgument = ArgumentInteger("seconds").setDefaultValue(30)
        val amplifierArgument = ArgumentInteger("amplifier").setDefaultValue(0)

        usage(usageString)

        subcommand("clear") {
            syntax(targetArgument) {
                val targets = get(targetArgument).find(sender).filterIsInstance<Player>()
                for (target in targets) {
                    target.clearEffects()
                }
                if (targets.size == 1) {
                    sender.sendMessage(
                        formatMessageTranslated(
                            "commands.effect.clear.everything.success.single",
                            targets.first().name
                        )
                    )
                } else {
                    sender.sendMessage(
                        formatMessageTranslated(
                            "commands.effect.clear.everything.success.multiple",
                            targets.size
                        )
                    )
                }
            }
            syntax(targetArgument, effectArgument) {
                val targets = get(targetArgument).find(sender).filterIsInstance<Player>()
                for (target in get(targetArgument).find(sender)) {
                    for (effect in target.activeEffects) {
                        if (effect.potion.effect == get(effectArgument)) target.removeEffect(effect.potion.effect)
                    }
                }
                if (targets.size == 1) {
                    sender.sendMessage(
                        formatMessageTranslated(
                            "commands.effect.clear.specific.success.single",
                            Component.translatable(get(effectArgument).registry().translationKey, BRAND_COLOR_PRIMARY_1),
                            targets.first().name
                        )
                    )
                } else {
                    sender.sendMessage(
                        formatMessageTranslated(
                            "commands.effect.clear.specific.success.multiple",
                            Component.translatable(get(effectArgument).registry().translationKey, BRAND_COLOR_PRIMARY_1),
                            targets.size
                        )
                    )
                }
            }
        }

        subcommand("give") {
            syntax(targetArgument, effectArgument, secondsArgument, amplifierArgument) {
                val potion = Potion(get(effectArgument), get(amplifierArgument).toByte(), get(secondsArgument) * 20, Potion.ICON_FLAG or Potion.PARTICLES_FLAG)
                val targets = get(targetArgument).find(sender).filterIsInstance<Player>()
                for (target in targets) {
                    target.addEffect(potion)
                }
                if (targets.size == 1) {
                    sender.sendMessage(
                        formatMessageTranslated(
                            "commands.effect.give.success.single",
                            Component.translatable(get(effectArgument).registry().translationKey, BRAND_COLOR_PRIMARY_1),
                            targets.first().name
                        )
                    )
                } else {
                    sender.sendMessage(
                        formatMessageTranslated(
                            "commands.effect.give.success.multiple",
                            Component.translatable(get(effectArgument).registry().translationKey, BRAND_COLOR_PRIMARY_1),
                            targets.size
                        )
                    )
                }
            }
        }
    })