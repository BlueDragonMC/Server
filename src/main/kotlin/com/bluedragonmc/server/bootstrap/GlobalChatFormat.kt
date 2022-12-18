package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.ALT_COLOR_2
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.service.Permissions
import com.bluedragonmc.server.utils.buildComponent
import com.bluedragonmc.server.utils.miniMessage
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent

object GlobalChatFormat : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerChatEvent::class.java) { event ->
            val player = event.player as CustomPlayer
            player.getFirstMute()?.let { mute ->
                event.isCancelled = true
                event.player.sendMessage(GlobalPunishments.getMuteMessage(mute).surroundWithSeparators())
                return@addListener
            }
            val experience = (player).run { if (isDataInitialized()) data.experience else 0 }
            val level = CustomPlayer.getXpLevel(experience)
            val xpToNextLevel = CustomPlayer.getXpToNextLevel(level, experience)

            val prefix = Permissions.getMetadata(player.uuid).prefix

            event.isCancelled = true
            val component = buildComponent {
                +buildComponent {
                    +Component.text("[", NamedTextColor.DARK_GRAY)
                    +Component.text(level.toInt(), BRAND_COLOR_PRIMARY_1)
                    +Component.text("] ", NamedTextColor.DARK_GRAY)
                }.hoverEvent(HoverEvent.showText(Component.translatable("global.chat_xp_hover",
                    NamedTextColor.GRAY,
                    event.player.name,
                    Component.text(experience, NamedTextColor.GREEN),
                    Component.text(xpToNextLevel, ALT_COLOR_1),
                    Component.text(level.toInt() + 1, ALT_COLOR_2))))

                +prefix
                +player.name
                +Component.text(": ", NamedTextColor.DARK_GRAY)
                if (Permissions.hasPermission(player.uuid, "chat.minimessage") == true)
                    +miniMessage.deserialize(event.message)
                else +Component.text(event.message, NamedTextColor.WHITE)
            }

            // Currently, setting the chat format does not allow for translation with GlobalTranslator
            // because it always sends a GroupedPacket.
            event.recipients.forEach { it.sendMessage(component) }
        }
    }
}