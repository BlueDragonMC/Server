package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.listen
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.metadata.PlayerMeta
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket
import net.minestom.server.network.packet.server.play.EntityRotationPacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.registry.RegistryKey
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
        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val player = event.entity as? CustomPlayer ?: return@addListener
            val npc = event.target as? NPC ?: return@addListener
            if (System.currentTimeMillis() - player.lastNPCInteractionTime > 1_000) {
                player.lastNPCInteractionTime = System.currentTimeMillis()
                npc.attackInteraction?.accept(NPCInteraction(player, npc))
            }
        }
    }

    override fun deinitialize() {
        super.deinitialize()
        npcList.forEach(NPC::remove)
    }

    private fun addNPC(npc: NPC) {
        npcList.add(npc)
    }

    fun addNPC(
        instance: Instance,
        positions: Collection<Pos>,
        customName: Component = Component.text("NPC"),
        skin: PlayerSkin? = null,
        entityType: EntityType = EntityType.PLAYER,
        interaction: Consumer<NPCInteraction>? = null,
        attackInteraction: Consumer<NPCInteraction>? = null,
        customNameVisible: Boolean = true,
    ) {
        positions.forEach { pos ->
            addNPC(NPC(instance, pos, customName, skin, entityType, interaction, attackInteraction, customNameVisible))
        }
    }

    fun addNPC(
        instance: Instance,
        position: Pos,
        customName: Component = Component.text("NPC"),
        skin: PlayerSkin? = null,
        entityType: EntityType = EntityType.PLAYER,
        interaction: Consumer<NPCInteraction>? = null,
        attackInteraction: Consumer<NPCInteraction>? = null,
        customNameVisible: Boolean = true,
        lookAtPlayer: Boolean = true,
        enableFullSkin: Boolean = true,
    ): NPC = NPC(
        instance,
        position,
        customName,
        skin,
        entityType,
        interaction,
        attackInteraction,
        customNameVisible,
        lookAtPlayer,
        enableFullSkin
    ).also { addNPC(it) }

    class NPC(
        instance: Instance,
        position: Pos,
        customName: Component = Component.text("NPC"),
        skin: PlayerSkin? = null,
        entityType: EntityType = EntityType.PLAYER,
        val interaction: Consumer<NPCInteraction>? = null,
        val attackInteraction: Consumer<NPCInteraction>? = null,
        private val customNameVisible: Boolean = true,
        private val lookAtPlayer: Boolean = true,
        enableFullSkin: Boolean = true,
    ) : LivingEntity(entityType, UUID.randomUUID()) {

        override fun isImmune(type: RegistryKey<DamageType>) = true
        override fun damage(type: RegistryKey<DamageType>, value: Float) = false
        override fun hasVelocity() = false
        override fun hasNoGravity() = false

        private val randomName = UUID.randomUUID().toString().substringBefore('-')
        private val addPlayerPacket = PlayerInfoUpdatePacket(
            PlayerInfoUpdatePacket.Action.ADD_PLAYER,
            PlayerInfoUpdatePacket.Entry(
                uuid,
                randomName,
                if (skin != null) listOf(
                    PlayerInfoUpdatePacket.Property(
                        "textures",
                        skin.textures(),
                        skin.signature()
                    )
                )
                else emptyList(),
                false,
                0,
                GameMode.CREATIVE,
                Component.text("[NPC] $randomName", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC),
                null,
                0
            )
        )

        init {
            if (enableFullSkin) enableFullSkin()
            if (customNameVisible) {
                val nametagDisplay = Entity(EntityType.TEXT_DISPLAY)
                val textDisplayMeta = nametagDisplay.entityMeta as TextDisplayMeta
                textDisplayMeta.setNotifyAboutChanges(false)
                textDisplayMeta.isHasNoGravity = true
                textDisplayMeta.text = customName
                textDisplayMeta.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
                textDisplayMeta.setNotifyAboutChanges(true)
                nametagDisplay.setInstance(instance, position.add(0.0, 2.0, 0.0)).join()
                nametagDisplay.eventNode().listen<EntityTickEvent> {
                    val nametagPosition = position.add(0.0, 2.0, 0.0)
                    if (nametagDisplay.position != nametagPosition) {
                        nametagDisplay.teleport(nametagPosition)
                    }
                }
            }

            val armorStand = Entity(EntityType.ARMOR_STAND)
            armorStand.isInvisible = true

            setInstance(instance, position).join()
            addPassenger(armorStand)
        }

        private fun enableFullSkin() {
            if (entityType != EntityType.PLAYER) return
            val meta = this.entityMeta as PlayerMeta
            meta.setNotifyAboutChanges(false)
            meta.isCapeEnabled = true
            meta.isHatEnabled = true
            meta.isJacketEnabled = true
            meta.isLeftLegEnabled = true
            meta.isLeftSleeveEnabled = true
            meta.isRightLegEnabled = true
            meta.isRightSleeveEnabled = true
            meta.setNotifyAboutChanges(true)
        }

        override fun tick(time: Long) {
            if (!lookAtPlayer) return
            instance.entityTracker.nearbyEntities(position, 5.0, EntityTracker.Target.PLAYERS) {
                val pos =
                    position.withY(position.y + eyeHeight).withLookAt(it.position.withY(it.position.y + it.eyeHeight))
                it.sendPacket(EntityHeadLookPacket(entityId, pos.yaw))
                it.sendPacket(EntityRotationPacket(entityId, pos.yaw, pos.pitch, onGround))
            }
        }

        override fun updateNewViewer(player: Player) {
            player.sendPacket(addPlayerPacket)

            MinecraftServer.getSchedulerManager().scheduleNextTick {
                player.sendPacket(passengersPacket)
            }

            super.updateNewViewer(player)
        }

    }

    data class NPCInteraction(val player: Player, val npc: NPC)

    enum class NPCSkins(val skin: PlayerSkin) {

        ZOMBIE(
            PlayerSkin(
                "ewogICJ0aW1lc3RhbXAiIDogMTY0MDI2OTY5OTg0OSwKICAicHJvZmlsZUlkIiA6ICIwMmIwZTg2ZGM4NmE0YWU3YmM0MTAxNWQyMWY4MGMxYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJab21iaWUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzgzYWFhZWUyMjg2OGNhZmRhYTFmNmY0YTBlNTZiMGZkYjY0Y2QwYWVhYWJkNmU4MzgxOGMzMTJlYmU2NjQzNyIKICAgIH0KICB9Cn0=",
                "DKIiqztp2XXi973AJeJ5jSGaLVIFAM+XyQGhRwmYOSlEo2Scc2YcaKi6gQCAmTtNeWlnf9wagZ8sezJzePANn0Yi3xMETd5OojATXKamNoQB7VsRRhXNl47WmOz5/DpZPk5yxVIPWo6jJCb7RwDkX/CIaYJPErA0tQOB8UyR2g37oZYgkHLqj80080scReh4KiZYs3ymfF/5vRUdkyBbaiVpeB87V4t4HFscoZt8iJlaa3fD8ZR0wbkMe7VGC5iafrXTGBbBMDlBYBkRtuR4Mqg2IRZpXFIh3FlNitW8x3hUsRHPDPBSGLgErjOnFtVafytt3Q2t3zc0jCmL8/wGzlppghVzK0IrAoAHCL7FCe1uGwFf8lRgTk7Vq2ZLFg0qxZN4dbO51vj7MT+MIUkP+7Zs2k2yxlmFdMGQiIsF37HVjfc6QdBVAfAFr2+1PcJ0ffRkgUTjyqL0UJv5qPqEJ9MBXhKwn4JlPigljvQIIYj/JxIWjJT1EgBKuv4M3g59jQL0vB4K9jeasI8vvXGAvTJFqa7KkumXKUoiZwSU4mVYrzxYlvQ2Ku14Q3pLl3BTsoeRkZq0YWabt+xHjMdK5srJZcV9AuGeIALMMxWGQA5riNAHQ5ZFpDq5vTYwfZn/+DsyG3MB5ftNTb2Dnsf5zbzpACW6uAbu/5csdKlrZMU="
            )
        ),

        WEIRD_FARMER(
            PlayerSkin(
                "ewogICJ0aW1lc3RhbXAiIDogMTYzNjU5NDU4OTM2OCwKICAicHJvZmlsZUlkIiA6ICIwNjNhMTc2Y2RkMTU0ODRiYjU1MjRhNjQyMGM1YjdhNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJkYXZpcGF0dXJ5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2E2OTAyMDkwYTdjNzM5ODAyNWRhYzJkYzg1YjBkZDMzZmEzY2VkYWFhOGQ0ZDJhNWE1ZGJlNzA1ZDJkNmU2NTIiCiAgICB9CiAgfQp9",
                "a9RNwZrK76Q2Bds5WBoV6j0cODD+fK5m3CUyz2C5fNG2+N6jIgmkcRKa+V4yNfPAdiUA6EnXc4h08aK/GDcoTTxa7APe5HAc7eUpxSL4fP983gFE7jGj2JxrYB2ZY6+GKnO0iQVHygvT4lkL8eBsB2GGQU2zX7j4wad6MzWiM4+KKxzusYLlJOBPXTyWFu7BE674pbm/zwr7E7ehjbK4MQV71yht94iJGT72tTP4y/0Twf4bEhm9899t/Lu/Z4PuuNnaY74V02yBkNv3RBx/8/58jM96PNGTHSDlNmum8Ua9ch4MecqqsBG0GJu4QvcOklKM+QxBwQ/PWB73zyXOz90Zy6Y89Gvmyjd8ap8kBkLmjVQsrKRK1R2j8DdytqLzuw4IFAMKm5IaP1SrPYEc6OVYoCpRCnla8VG0Ws2r+uitfzyYzPUqbXLgSi6kvPDt59yffJmgYZTmPSqsPDfX6+zq9PaUce3t/viXpIAa8qaV56yaC0SJEnBHYgbeuRKm+PncS36C6vfvwO0I19SMcz5jN7/XJiHHpGEBYCwLTmK1FUnBBqL5VbvnxTFomftqOjyoPKL7IzVIHmRrss4B38ldcHthxM4aSlNqsroID9tOnX+udYdMfl4qzigifUBINE8pj/WDAtQKrmeNTJUS7NPC1syEzFFqQKdFT8ho9x4="
            )
        ),

        MINDECRAFTES(
            PlayerSkin(
                "ewogICJ0aW1lc3RhbXAiIDogMTY1ODYyMTU4MjU2NywKICAicHJvZmlsZUlkIiA6ICIzOTdlMmY5OTAyNmI0NjI1OTcyNTM1OTNjODgyZjRmMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJ4WnlkdWVMeCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yYzAwOThjYjMyNGYxNGZmNTQ3N2QwZDA3NGNkZjVjMjc4Yjk0NmI5MjQyNjViNmRkNDljNmFkNDFmMTA1YTYxIgogICAgfQogIH0KfQ==",
                "W8LT6UyN90VdflXZG9gGnzLdsLd9A6z5ROvfzbNWBcrfN1qHuwqmqxPnWYknwuGI9QL24tvTUlsZI8TwvLe/gcz4HUUUzLtgjHjsS11Pr4QabVfJ8kgQgtF63TaqQw0udIFCPhNiaPkCQVNLnBYKfNlry1WrUnrZK0jt3hWvx65Yh93hwxjpw/dRaE3baXgWVj+1zLWT3X3DcnqqvETFboenG6rJvzkcqN4xrk75/DnaZg1fal3KHtTlDnzShvNdFSnTU98phOMWkkUeyCJk9nOOuXY0OIbHVNVNBH1p2mI9FxTrJKqM+8FooFLTksLvU+9McMiB3sYdOrg4LrLx0d6x0uKYD7FZR5FtQ1nKsgY8e1Y+bYoeT8oEO/Dvl2slruWlg475S3etnryS5htAC6Ngfishvbj0bd2c5xgUQzLjw58Ff2OIZAt8YAV1CzidRzJGfAm6PIN+j/O2agHmu+tIpsdKncDoIzE/jQh3sV+sG+kNdqJMR0KrD/cumM9XtpvjjhI+yn/meyh5c8ufq+hD4xBnTu13ZDGmfWhCzjVnMMJSJRBuS5qlHh+1ugSiMdQJ0+FnEMpRa3Vy7sOJeHme0c3LkGrRPonVBevoEqpOjzDaUYamutkVhcBZ0RR4lCDPI0MCqR8fN4AcLQTG3hD1hXZbXyOEjElFsGJlkQI="
            )
        ),

    }
}