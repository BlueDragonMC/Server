package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
import net.minestom.server.entity.metadata.PlayerMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.EntityTeleportPacket
import net.minestom.server.network.packet.server.play.PlayerInfoPacket
import java.util.*
import java.util.function.Consumer

/**
 * A module that allows NPCs to be added into the world.
 * Example implementation of NPCs:
 * ```
 * use(NPCModule())
 * getModule<NPCModule>().addNPC(position = Pos(2.5, 64.0, 7.5), customName = Component.text("NPC woohoo!"), interaction = {
 * it.player.sendMessage("WOOHOO!")})
 * ```
 */
class NPCModule : GameModule() {

    private lateinit var parent: Game
    private val npcList: MutableList<NPC> = mutableListOf()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            if (System.currentTimeMillis() - (event.player as CustomPlayer).lastNPCInteractionTime > 1_000) {
                (event.player as CustomPlayer).lastNPCInteractionTime = System.currentTimeMillis()
                (event.target as? NPC)?.interaction?.accept(NPCInteraction(event.player, event.target as NPC))
            }
        }
    }

    override fun deinitialize() {
        super.deinitialize()
        for (npc in npcList) npc.remove()
    }

    fun addNPC(instance: Instance = parent.getInstance(), position: Pos, npc: NPC) {
        npc.setInstance(instance, position)
        npcList.add(npc)
    }

    fun addNPC(
        instance: Instance = parent.getInstance(),
        position: Pos,
        uuid: UUID = UUID.randomUUID(),
        customName: Component = Component.text("NPC"),
        skin: PlayerSkin? = null,
        entityType: EntityType = EntityType.PLAYER,
        interaction: Consumer<NPCInteraction>? = null,
        customNameVisible: Boolean = true
    ) {
        addNPC(instance, position, NPC(uuid, customName, skin, entityType, interaction, customNameVisible))
    }

    class NPC(
        uuid: UUID = UUID.randomUUID(),
        customName: Component = Component.text("NPC"),
        private val skin: PlayerSkin? = null,
        entityType: EntityType = EntityType.PLAYER,
        val interaction: Consumer<NPCInteraction>? = null,
        private val customNameVisible: Boolean = true,

        ) : LivingEntity(entityType, uuid) {

        private val addPlayerPacket: PlayerInfoPacket = PlayerInfoPacket(
            PlayerInfoPacket.Action.ADD_PLAYER, PlayerInfoPacket.AddPlayer(
                uuid,
                customName.let { LegacyComponentSerializer.legacySection().serialize(it) },
                if (skin != null) listOf(
                    PlayerInfoPacket.AddPlayer.Property(
                        "textures",
                        skin.textures(),
                        skin.signature()
                    )
                )
                else emptyList(),
                GameMode.CREATIVE,
                0,
                customName
            )
        )
        private val removePlayerPacket: PlayerInfoPacket = PlayerInfoPacket(
            PlayerInfoPacket.Action.REMOVE_PLAYER, PlayerInfoPacket.RemovePlayer(
                uuid
            )
        )

        init {
            setCustomName(customName)
            enableFullSkin()
        }

        fun enableFullSkin() {
            val meta = PlayerMeta(this, this.metadata)
            meta.isCapeEnabled = true
            meta.isHatEnabled = true
            meta.isJacketEnabled = true
            meta.isLeftLegEnabled = true
            meta.isLeftSleeveEnabled = true
            meta.isRightLegEnabled = true
            meta.isRightSleeveEnabled = true
            meta.customName = customName
            meta.isCustomNameVisible = customNameVisible
        }

        override fun updateNewViewer(player: Player) {

            player.sendPacket(addPlayerPacket)

            // If the player can see the entity, it can be removed from the tablist as its skin is already being loaded.
            if(hasLineOfSight(player, true) || skin == null) {
                player.sendPacket(removePlayerPacket)
                return
            }

            val parent = player.eventNode()
            val child = EventNode.event(
                "temp-npc-register",
                EventFilter.PLAYER
            ) { event -> event is PlayerMoveEvent }
            parent.addChild(child)

            // If the player has no line of sight to the NPC, it will be teleported into the player's line of sight
            // for one tick and teleported back shortly after. This must occur after the player has moved once because
            // Minecraft does not load NPC skins while in loading screens.
            child.addListener(PlayerMoveEvent::class.java) {
                parent.removeChild(child) // Immediately unregister this event handler after it's triggered
                player.sendPacket(EntityTeleportPacket(entityId, player.position.add(
                    player.position.direction().normalize().mul(16.0)
                ), false))

                player.sendPacket(EntityTeleportPacket(entityId, position, onGround))
                player.sendPacket(removePlayerPacket)
            }

            super.updateNewViewer(player)
        }

    }

    data class NPCInteraction(val player: Player, val npc: NPC)
}