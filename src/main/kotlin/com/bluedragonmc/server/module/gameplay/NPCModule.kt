package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.SingleAssignmentProperty
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
import net.minestom.server.entity.metadata.PlayerMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.PlayerInfoPacket
import net.minestom.server.utils.time.TimeUnit
import java.util.*
import java.util.function.Consumer

/**
 * A module that allows NPCs to be added into the world.
 * Example implementation of NPCs:
 * ```
 * use(NPCModule())
 * getModule<NPCModule>().addNPC(position = Pos(2.5, 64.0, 7.5), customName = Component.text("NPC woohoo!"), interaction = {
it.player.sendMessage("WOOHOO!")})
 ```
 */
class NPCModule : GameModule() {

    private var parent by SingleAssignmentProperty<Game>()
    private val npcList: MutableList<NPC> = mutableListOf()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            if (event.target is NPC) {
                (event.target as NPC).interaction?.accept(NPCInteraction(event.player, event.target as NPC))
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

    fun addNPC(instance: Instance = parent.getInstance(), position: Pos, uuid: UUID = UUID.randomUUID(),
               customName: Component = Component.text("NPC"),
               skin: PlayerSkin? = null, interaction: Consumer<NPCInteraction>? = null, customNameVisible: Boolean = true) {
        addNPC(instance, position, NPC(uuid, customName, skin, interaction, customNameVisible))
    }

    class NPC(
        uuid: UUID = UUID.randomUUID(),
        customName: Component = Component.text("NPC"),
        skin: PlayerSkin? = null,
        val interaction: Consumer<NPCInteraction>? = null, // Register this manually with a PlayerEntityInteractEvent
        private val customNameVisible: Boolean = true,

        ) : LivingEntity(EntityType.PLAYER, uuid) {

        private val addPlayerPacket: PlayerInfoPacket
        val removePlayerPacket: PlayerInfoPacket

        init {
            setCustomName(customName)
            enableFullSkin()

            addPlayerPacket = PlayerInfoPacket(
                PlayerInfoPacket.Action.ADD_PLAYER,
                PlayerInfoPacket.AddPlayer(
                    uuid,
                    customName.let { LegacyComponentSerializer.legacySection().serialize(it) },
                    if (skin != null)
                        listOf(PlayerInfoPacket.AddPlayer.Property("textures", skin.textures(), skin.signature()))
                    else
                        listOf(),
                    GameMode.CREATIVE,
                    0,
                    customName
                )
            )

            removePlayerPacket = PlayerInfoPacket(
                PlayerInfoPacket.Action.REMOVE_PLAYER,
                PlayerInfoPacket.RemovePlayer(
                    uuid
                )
            )
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

            MinecraftServer.getSchedulerManager().buildTask {
                player.sendPacket(removePlayerPacket)
            }.delay(100, TimeUnit.SERVER_TICK).schedule() // if the skins don't load, increase this number
            super.updateNewViewer(player)
        }

    }

    data class NPCInteraction(val player: Player, val npc: NPC)
}