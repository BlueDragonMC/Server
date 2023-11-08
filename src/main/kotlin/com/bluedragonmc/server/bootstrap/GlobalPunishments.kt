package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.model.Punishment
import com.bluedragonmc.server.utils.buildComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object GlobalPunishments : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(DataLoadedEvent::class.java) { event ->
            val player = event.player as CustomPlayer
            val ban = player.getFirstBan()
            if (ban != null) {
                player.kick(getBanMessage(ban))
            }
        }
    }

    private fun getPunishmentMessage(titleKey: String, punishment: Punishment) = buildComponent {
        +Component.translatable(titleKey, NamedTextColor.RED, TextDecoration.UNDERLINED)
        +Component.newline()
        +Component.newline()
        +Component.translatable("punishment.field.reason", Component.text(punishment.reason, NamedTextColor.WHITE))
        +Component.newline()
        +Component.translatable("punishment.field.expiry", Component.text(punishment.getTimeRemaining(), NamedTextColor.WHITE))
        +Component.newline()
        +Component.translatable("punishment.field.id", Component.text(punishment.id.toString().substringBefore('-'), NamedTextColor.WHITE))
        // Kick messages with a non-breaking space (U+00A0) will prevent the player from being immediately connected to another server
        // This is a way of differentiating intentional vs. accidental kicks that remains invisible to the end user
        +Component.text("\u00A0")
    }

    fun getBanMessage(punishment: Punishment) = getPunishmentMessage("punishment.ban.title", punishment)
    fun getMuteMessage(punishment: Punishment) = getPunishmentMessage("punishment.mute.title", punishment)
}