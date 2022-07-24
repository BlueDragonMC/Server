package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.hologram.Hologram
import net.minestom.server.entity.metadata.PlayerMeta
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket
import net.minestom.server.network.packet.server.play.EntityRotationPacket
import net.minestom.server.network.packet.server.play.PlayerInfoPacket
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
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
        npcList.forEach(NPC::remove)
    }

    fun addNPC(npc: NPC) {
        npcList.add(npc)
    }

    fun addNPC(
        instance: Instance = parent.getInstance(),
        positions: Collection<Pos>,
        customName: Component = Component.text("NPC"),
        skin: PlayerSkin? = null,
        entityType: EntityType = EntityType.PLAYER,
        interaction: Consumer<NPCInteraction>? = null,
        customNameVisible: Boolean = true,
    ) {
        positions.forEach { pos ->
            addNPC(NPC(instance, pos, customName, skin, entityType, interaction, customNameVisible))
        }
    }

    fun addNPC(
        instance: Instance = parent.getInstance(),
        position: Pos,
        customName: Component = Component.text("NPC"),
        skin: PlayerSkin? = null,
        entityType: EntityType = EntityType.PLAYER,
        interaction: Consumer<NPCInteraction>? = null,
        customNameVisible: Boolean = true,
        lookAtPlayer: Boolean = true,
    ) {
        addNPC(NPC(instance, position, customName, skin, entityType, interaction, customNameVisible, lookAtPlayer))
    }

    class NPC(
        instance: Instance,
        position: Pos,
        customName: Component = Component.text("NPC"),
        skin: PlayerSkin? = null,
        entityType: EntityType = EntityType.PLAYER,
        val interaction: Consumer<NPCInteraction>? = null,
        private val customNameVisible: Boolean = true,
        private val lookAtPlayer: Boolean = true,
    ) : LivingEntity(entityType, UUID.randomUUID()) {

        override fun isImmune(type: DamageType) = true
        override fun damage(type: DamageType, value: Float) = false

        private val randomName = UUID.randomUUID().toString().substringBefore('-')
        private val addPlayerPacket: PlayerInfoPacket = PlayerInfoPacket(PlayerInfoPacket.Action.ADD_PLAYER,
            PlayerInfoPacket.AddPlayer(uuid,
                randomName,
                if (skin != null) listOf(PlayerInfoPacket.AddPlayer.Property("textures",
                    skin.textures(),
                    skin.signature()))
                else emptyList(),
                GameMode.CREATIVE,
                0,
                Component.text("[NPC] $randomName", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)))
        private val removePlayerPacket: PlayerInfoPacket =
            PlayerInfoPacket(PlayerInfoPacket.Action.REMOVE_PLAYER, PlayerInfoPacket.RemovePlayer(uuid))

        private lateinit var hologram: Hologram

        init {
            enableFullSkin()

            val armorStand = Entity(EntityType.ARMOR_STAND)
            val standMeta = armorStand.entityMeta as ArmorStandMeta
            standMeta.setNotifyAboutChanges(false)
            standMeta.isSmall = true
            standMeta.isInvisible = true
            standMeta.setNotifyAboutChanges(true)

            setInstance(instance, position).join()
            addPassenger(armorStand)

            if (customNameVisible) {
                hologram = Hologram(instance, position.withY(position.y + eyeHeight + 0.2), customName, true)
                hologram.entity.isAutoViewable = false
            }
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

            MinecraftServer.getSchedulerManager().buildTask {
                player.sendPacket(removePlayerPacket)
            }.delay(Duration.ofSeconds(5)).schedule()

            super.updateNewViewer(player)
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                // Why is this necessary when the super function sends this packet? I have no idea.
                player.sendPacket(passengersPacket)
                if (customNameVisible) hologram.addViewer(player)
            }
        }

        override fun updateOldViewer(player: Player) {
            if (customNameVisible) hologram.removeViewer(player)
            super.updateOldViewer(player)
        }

        override fun remove() {
            if (customNameVisible)
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    if(!hologram.isRemoved) hologram.remove()
                }
            super.remove()
        }

        override fun teleport(position: Pos, chunks: LongArray?): CompletableFuture<Void> {
            if (customNameVisible) hologram.position = position
            return super.teleport(position, chunks)
        }

        override fun refreshPosition(newPosition: Pos, ignoreView: Boolean) {
            if (customNameVisible) hologram.entity.refreshPosition(newPosition, ignoreView)
            super.refreshPosition(newPosition, ignoreView)
        }
    }

    data class NPCInteraction(val player: Player, val npc: NPC)

    enum class NPCSkins(val skin: PlayerSkin) {

        EX4(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTU4ODczNDk5Nzk5MCwKICAicHJvZmlsZUlkIiA6ICJhMDA0ODE0MzQ2MGY0MTdlOTExOWYzMGViOTY3NGE3YSIsCiAgInByb2ZpbGVOYW1lIiA6ICJza2F0ZXI3N2kiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjhkNmRiMzQwM2VlNTYyYTY3ODAxYTNkODczNWRhN2YwYTAyOGMwZDVmZmUyNzc3OGZmODMzYWU5YjM5NjY3MSIKICAgIH0KICB9Cn0=",
            "VmLuwGvhhNk9EIfbrvOek2GIJH0kbUn3wQgmPdw9B7tdWKB4eQFioSUPWX2GAcaqVUphVY9RBmQosx1DaA10NpnzZWgRpcSmBz2//oKDuxzonmucYx8zr/0/7Jw2nZxhcGB4+K4d1bQRMCx+PslEZJ2z+hQHoXG+cis4QAFzackqqi1rkNoB6wK/txf0iFusQOYKIPtigpiBlNmz13u+WtqDA1L7e5e1SkcywQYKyPOQgPfVEfolLCC+6T0wwhzHOIvNSSKbUPuMd0ntJbb5t3XrcwZ9nsc1oJjvMeQGu1xP4QNiKwJX3HIzh0R8rNZh+I3VmG4E0G6RGFge7Dq2KGkW4TevCTa5f5Gs5j5Av3R6Bz46JHguqfj2joezNVSTTudW9eLxC3ACatQNcKgOtIgQh2n/wzGDeHE1ioD6hfYT2CE50c64DE9/hN6oKh8XkCZarG39LeBq+QL8fz7KAfSrNeGw1l8q6YKWtDu247vVal1U2EIv//NmAkO14Q1n/lRO3aLCqhb5dU0n7+f9GXaNrbwHC/kT7mXbjGp8uWD+hb5UHgXvLVHCXF68YUA6yqz2InD+nn3Dv8laJZ6c09wR+mx5Xc/rGSppRzahQdX7WnbHlzgguRJVhFCpsxPbWQnw6G5M84IR4ACkPjSaijom0wLUZJIUBP+uTPAEeoc=")),

        MONKEY(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTU5MTc5Mjk5NDEyMSwKICAicHJvZmlsZUlkIiA6ICI2ZmQyNGJlNDk4ZjA0MDJlOTZhYWQ2MWUzY2VmYjZmMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbmdlbGFsbHhfIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2I5OWViNmUwYTU1ZWIyMjM5MzZiYzMxNjcyY2Q0NDkyN2Y3N2IxYTUyYjVkZTIwNjYyZTQzYmE1ZGNiYTdlNDciCiAgICB9CiAgfQp9",
            "NaXCznwy4NV0zpk2lerX1A8qGYiHE5fqYOhcs3vfh2eus1wQ6LehIwbp8TzsHhk7rW+0xMBnxvfowcxosTpVg5UTEtD2MwSvszqGxziY14p0HdvX410JC0hEvgxVvaJzv4YLNGmWnxl8frolUPAYWCI/S/F0hWx6Zav7c5WYSXUTu75wVLMWrtLGSPhTLnFxiWrb4tzRCsZgipM+i+N1VlcyCKJAS/2vCZQRBK+SevyJl2VtNhrZCfRJ0dS7V7Ld2+ydXf4zVLp4KlSiVWOUcTgaQDF0vavBJuXjHCHmEPsfBuxX2KOaOriz8OHbN6fLAUMPEjbdS3hE7RZG/15XTICstlsBYaG/CxH8/W0NrN9muthJl1KoYl6QaC2jMJfyutQl8edk5wYGLpjuatKC1YRPqSPgSjVznHLIvYFtpFYlyKD2cJ6w/JrmGUhGT+GjFs13XGrTGwv9pE8QFBf7Gl5N/+k7u6Yrj4JuhNB5GSwtR/LTJKm/3DQUesKDWQeFccSJAosA4VKZNMh+tIDA/hc2Mz0DXvqSBkB5Yz8/kS8CF4LrQGl2o8GxzUXeRujxL7z8u8lhjc/jmfwIGIqKTAtNODeJXnJaJbsFX2yMpeKmhUvvzDuOJr0Q2QUdHLsEc44vqKtY4v/7zgb3M7zJDD4E80xsJPebfCeVqMuO4EM=")),

        ZOMBIE(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTY0MDI2OTY5OTg0OSwKICAicHJvZmlsZUlkIiA6ICIwMmIwZTg2ZGM4NmE0YWU3YmM0MTAxNWQyMWY4MGMxYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJab21iaWUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzgzYWFhZWUyMjg2OGNhZmRhYTFmNmY0YTBlNTZiMGZkYjY0Y2QwYWVhYWJkNmU4MzgxOGMzMTJlYmU2NjQzNyIKICAgIH0KICB9Cn0=",
            "DKIiqztp2XXi973AJeJ5jSGaLVIFAM+XyQGhRwmYOSlEo2Scc2YcaKi6gQCAmTtNeWlnf9wagZ8sezJzePANn0Yi3xMETd5OojATXKamNoQB7VsRRhXNl47WmOz5/DpZPk5yxVIPWo6jJCb7RwDkX/CIaYJPErA0tQOB8UyR2g37oZYgkHLqj80080scReh4KiZYs3ymfF/5vRUdkyBbaiVpeB87V4t4HFscoZt8iJlaa3fD8ZR0wbkMe7VGC5iafrXTGBbBMDlBYBkRtuR4Mqg2IRZpXFIh3FlNitW8x3hUsRHPDPBSGLgErjOnFtVafytt3Q2t3zc0jCmL8/wGzlppghVzK0IrAoAHCL7FCe1uGwFf8lRgTk7Vq2ZLFg0qxZN4dbO51vj7MT+MIUkP+7Zs2k2yxlmFdMGQiIsF37HVjfc6QdBVAfAFr2+1PcJ0ffRkgUTjyqL0UJv5qPqEJ9MBXhKwn4JlPigljvQIIYj/JxIWjJT1EgBKuv4M3g59jQL0vB4K9jeasI8vvXGAvTJFqa7KkumXKUoiZwSU4mVYrzxYlvQ2Ku14Q3pLl3BTsoeRkZq0YWabt+xHjMdK5srJZcV9AuGeIALMMxWGQA5riNAHQ5ZFpDq5vTYwfZn/+DsyG3MB5ftNTb2Dnsf5zbzpACW6uAbu/5csdKlrZMU=")),

        SKY(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTU5MTk3NzgyMjU0NCwKICAicHJvZmlsZUlkIiA6ICI1NjY3NWIyMjMyZjA0ZWUwODkxNzllOWM5MjA2Y2ZlOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVJbmRyYSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jODA3NTdkYzk5YWMzZTBhMTRiMThkYjFhMmE1MTA1MGQ0NmMwMjFjMmQ2OGI4NTQ1ZDY0ZTNjNTMwNTExMjZjIgogICAgfQogIH0KfQ==",
            "wZpdk+lBga7MTiITrCMYfdTXLUWrNpWJ1IrXYEjEsL2aZM8q21AbBNGVmEUqobHp+Z6lNxHc6pwPn/u7qNmlRhV130cq5Arm+tQQePRYZmzGt7SyP2/NImWdYbhTTLo6hjAK1Ifj11pFHzJXLjatufSelQWtmK5si3WTkphRV8iH1GdH0aksas6xPERqJ6lhi4aWKPHEHpNuK/6etRcTDW6pJmTbOcCRZCi7N/DlAbdmT1Klvbxm5fyDaBO/1uIv1TNe0SMYH6v38GAg+gfow6BBDCBsqATzdk0dYAxP4xxLFRcBxqgSz/yK1ZqOc2lFllutxZXjf3ucqR/Gh9iNB31zXtuwX3bdZVMeMLuNUqGiDUtQYRM37twMqLdk07jfkJBMpj3MIgTWa8jGWJgS4kMcGT134eMAfBvw0Q17TqfVdzcc+lCGsVYK5VNcx8wn/BXIGZsqITG4Jsk7kpqxE39he9iRAWNH4myylhdyrbS+USkGzYI5J1lZcGfGllGogwZplfWt7bQSfedgSDTBbWblVM5OJMo0TAQK2tqukhunrSk+w+lW8TXnylpcqTL8BIxtTerSjyxPHtaclzK8kXweONzAM84/uuA7WELgMzk4fNysT0pGZthp2O4UPPCCdwka6z9JoLT26Klx4yEik5tn4oc9oz3kQwx+UZzBIK8=")),

        CRAZY_SHARK(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTY0MDgwMzAwMjQ1MywKICAicHJvZmlsZUlkIiA6ICIwMmU4OGJlNjMwMTU0NGJkODdiYmQ4YzgxMjIwODA2MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJjcmF6eXNoYXJrMzIxIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2Q4NmU0NWU3OTcxNWI2MjJjMWRmZjY2YzZjNWZkMTNiNTZmMDI0MmFlMjUyODdiMjNiZjAzZWYzMGFkYjJiMWEiCiAgICB9LAogICAgIkNBUEUiIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzIzNDBjMGUwM2RkMjRhMTFiMTVhOGIzM2MyYTdlOWUzMmFiYjIwNTFiMjQ4MWQwYmE3ZGVmZDYzNWNhN2E5MzMiCiAgICB9CiAgfQp9",
            "A85MviayKp9Qtx7uCcPqO8WXNzoa7GpiKc+1wlPQXn6Z2LkQp/C262R15zYNvej2AjEjCnTNkW3s+zgjM8VoC3EmWmrXzXB/ZAWE7ithNC/qTnFOOMka+hbBbUPNSBB6kM+6HiFqZjAHJo5V5mspamvFFXjgy+yJtciAxJmtJsxaXv0u4zjF6C5bfkHnq8elvFgiyJDD0loG5A6bp1/FkeJGf5veTlXfoxCS5tyA1Cm5cRMJFlOf/6m3sFsJI7um67nTC3y4cDyTQYZYWNKMSC7XMB25ERn0M5cqZ1aoUGYvJloiFMhbniJ6lfgfjYzmCzpVu3DCH3eEHZASiFLC3FW3z0dF0R4oW/2gwJfY99kPn3H7AunDMkU3lgWOPyvagNf9UPaSEuNp/OvM4N01dMGF5ryD7fujNTaGkIV/cTVjyQzeFFeOb0jtEOlCNrmWYON1WrK0WlZ6W+vveMd0X4LRKchrpCiEvc+O65lGUuLwU1wchbaXp72qlejXCyC4NERb2ty2bgIUPnIx5rBjXKBK15T+YpFZCW5c+dkOUvQOclcLGwQrPUGpxWIY8yD5QOKuZYAZb/9MjogSmayE57Yj+dKMqsqXzOaJbNMznQITQ4Tj2jOWCCubIWiAbWOpk42BUYFnq/DFEVOoeUvg6tzofd+jr6I/PcPySa72gGI=")),

        WEIRD_FARMER(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTYzNjU5NDU4OTM2OCwKICAicHJvZmlsZUlkIiA6ICIwNjNhMTc2Y2RkMTU0ODRiYjU1MjRhNjQyMGM1YjdhNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJkYXZpcGF0dXJ5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2E2OTAyMDkwYTdjNzM5ODAyNWRhYzJkYzg1YjBkZDMzZmEzY2VkYWFhOGQ0ZDJhNWE1ZGJlNzA1ZDJkNmU2NTIiCiAgICB9CiAgfQp9",
            "a9RNwZrK76Q2Bds5WBoV6j0cODD+fK5m3CUyz2C5fNG2+N6jIgmkcRKa+V4yNfPAdiUA6EnXc4h08aK/GDcoTTxa7APe5HAc7eUpxSL4fP983gFE7jGj2JxrYB2ZY6+GKnO0iQVHygvT4lkL8eBsB2GGQU2zX7j4wad6MzWiM4+KKxzusYLlJOBPXTyWFu7BE674pbm/zwr7E7ehjbK4MQV71yht94iJGT72tTP4y/0Twf4bEhm9899t/Lu/Z4PuuNnaY74V02yBkNv3RBx/8/58jM96PNGTHSDlNmum8Ua9ch4MecqqsBG0GJu4QvcOklKM+QxBwQ/PWB73zyXOz90Zy6Y89Gvmyjd8ap8kBkLmjVQsrKRK1R2j8DdytqLzuw4IFAMKm5IaP1SrPYEc6OVYoCpRCnla8VG0Ws2r+uitfzyYzPUqbXLgSi6kvPDt59yffJmgYZTmPSqsPDfX6+zq9PaUce3t/viXpIAa8qaV56yaC0SJEnBHYgbeuRKm+PncS36C6vfvwO0I19SMcz5jN7/XJiHHpGEBYCwLTmK1FUnBBqL5VbvnxTFomftqOjyoPKL7IzVIHmRrss4B38ldcHthxM4aSlNqsroID9tOnX+udYdMfl4qzigifUBINE8pj/WDAtQKrmeNTJUS7NPC1syEzFFqQKdFT8ho9x4=")),

        WIZARD(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTYzODI5Mjk1NTU3NSwKICAicHJvZmlsZUlkIiA6ICJiYzRlZGZiNWYzNmM0OGE3YWM5ZjFhMzlkYzIzZjRmOCIsCiAgInByb2ZpbGVOYW1lIiA6ICI4YWNhNjgwYjIyNDYxMzQwIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzk0NmMzMWFmYjQ4YTU5ODMwZWYxM2JlYTRhY2QzYTEzMGRjMjdjZDNiMDllM2ZiZjUyY2E4Yzc2ZTA2OWI4ZjMiCiAgICB9CiAgfQp9",
            "vnGmKa2yHVoW8V6FTVWK/0o9vEJFJ90CY8eCrAufbQhuKaMPidaGglgngQxb7yWJ2F7PzpJd/yRGyj/YIdE5BaasU8jNrrUYj/4L2DycosJdXDXGOjbp44FxLRGoziiDoGIQ9BUlp4y8aQjLzF+HbRPEiYxErbITWSU0jZHgl+Pr+Oyysi5xeaJuEyQ9bl3rmmXT5hcX/920EOMNVC1NHAj0x7AU+Pbvb5b6ct9ANvI97dUiGF/hR63MiQP9Zrao08r8jL0z64r4mOxpubq14buY55/V5hk+QkMdRCZ4I0HDLJ5So0l+IjIhKXqIUXzcxhEWradrInX5lHLSVSyaR3p8h1JdJYI74BhCdy0guKbRrcLs3IxcKdlBjIFG1BB635SU0Qhmq88uJK3R9iOVTnB4yAkMrTsKpFKyr3uHoWz8IYRxc+WtVKwCNB0l7e/dO+n18HfBOdfaQnW/ptDzG2UozdLYSR5G9NO5m050SJ09CijokytweUMJAV8abeQWTQWaH8d06QwppIrCzdveDXTPKlQLMJTeKHzY9xXXR5zxYtq21e4RI72ebB66qmj+tSJUjYRoata0D1bWhUu/YQOMlxxuFwNybJyfnTnrsuCMjldgXuspzs4/dEE9sBb8ggVlrjWXfjuB99TqgRNHDcqjD0XZ3iQRYb7KEMrlN8g=")),

        BAKER(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTYzNjkxNTM4ODQyMiwKICAicHJvZmlsZUlkIiA6ICI4ZDYzN2M2MTRhYjA0ODczYjY2NDQ0OWU3MWU1YTg4MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJCYWtlciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iNmU4MmQ1YzM4YjIwMDhkZTU2N2M1M2RmZTg0YmIyOGFhZDg4M2FlYTc1YzRhMjA4ODM1ZTY1YTBhZDJmYmM1IgogICAgfSwKICAgICJDQVBFIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9lN2RmZWExNmRjODNjOTdkZjAxYTEyZmFiYmQxMjE2MzU5YzBjZDBlYTQyZjk5OTliNmU5N2M1ODQ5NjNlOTgwIgogICAgfQogIH0KfQ==",
            "Rb02OmftGuQRmY5zg0Dfj52HkJkuyyByxHz1HS8CokooN0G4pYV9TVEkJ8FLCIT6LGrnP55wbNSYCU5bws2izuT7A7tqhBJnY7XqPOO8CsYwfsIC/VIzHOWAPeSh/s8b73TNmXXfDDIMxKqwD6M9NqJD1tugu57dGiQJKIgZ/YArZh6XQsyRDCy/R1WvmI+nCWlIx14DFZn3JTA+8K4gSKJFIheHgFv81YnpmabCu7sPxawUGDX4pS1586Qbhk04Ii50Es1oee5S0WCe+k8KXMoPyIDkekOSzAMmXQ40+NVZDTBZQ5E0aZUtrC+ZN2Ax4sp/xTHDhzmIUEiEoeGpQPdHAATUuuP27F7mJIVAY9TqGMc6mENyPJKUcc8tC2BglzlSW0jOPTyfAmQMNs74zNs2xuRXyE9Knn00TjnIifmaPw9Q0HRZkedghc7yMvl+gXS6F0SbzW1eMR37nqS+kpqOQOuhczYZn1bopMtA1GORRS0CTc3RRVioiymA5MCGoMMTdsUrQ3phtGGh0iNG1F3bPGPenz/Sm5JVUGWNfR0ghKwtIFges5V0nfoKHdYSAmbyDIvC0iWJAoCIJLkdkci3NpocK+fFQyNskehJTn7PgXMgWlybZJjSHBIVqV1RcFxzd1ftCfrqTb6Xr4xaJ/Mp+51+B4sJ29mPZUoYlXY=")),

        SNOWMAN(PlayerSkin("eyJ0aW1lc3RhbXAiOjE1NTM2MzY4NzAwMTksInByb2ZpbGVJZCI6IjJjMTA2NGZjZDkxNzQyODI4NGUzYmY3ZmFhN2UzZTFhIiwicHJvZmlsZU5hbWUiOiJOYWVtZSIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTBhZTkxODQxYWZkMjQzZWMxOTgyYTc5ZmM0NTgyM2I0ZDM3NGY2MjFlMGQ3MzQ1MzNmODZkNzI1YTZmMGE0ZiJ9fX0=",
            "K57dzAOxUBjVRMEZS3AvJ31fj1hAG6U/iO40pan4AlVowQ7He4v5eCPwTST31LVKKAwTAParKdCt4zKKlcX2CdFQJpdyPMAGD8GDq8e/Ko9j18CIUz07T4So2Cixqf2NaGzMsLo3VyE/cljHeeT6YCgfeWyXGzVw7P8kqd0TUKH3D3d+BxakqkJf5VLdsWIhMRGMXsdu4RR7crvEAJDCS6FUB1jbZDxEpK/709wD/Liu8dvCpXgGsotb43oF7e8StEbe/WWcIooVRagsloCvyBh/bSvWK38wQgs9w2U41L/Steql2OpJfT6OSJCMr/vKnLcK0s79BpFLtEo399MhV45YYd1decvt8fJlLXPL015y3jBCroQuKHEsOwU6MbmSZ/51YCuFQNYEbhRMP5aqPvBHBRiCtRWkF1Z4nXkbCXOZi3WbJ5M/O7HxsZouharKg2Pxs8RjBvKvuf8i+DICQpDIT/RxwIMaXkzJnFNLyE5j+RaKBEWja/Mvn4umtqnZUfDRu4bwStesSrSOhGgDMnUZBGcOwH7ACIcYGLYQwpzNvTT46KdpnW9y0quMcVrirhYXpOS2oKbHKwHE9505mb+vSZRx0mU0MXuC2W6dsx29g10UT18JiUW7sCk089XKs9uQfW2LA0wfNrWw8+GYZTrUHnlB2LYx/ShbB5nQr48=")),

        BED_HEAD(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTY1NjUxMTY2MTA3NCwKICAicHJvZmlsZUlkIiA6ICI4YWFlYTdlYjViOWM0ZWEwODUxNWU3MDhhZGIxODBkNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJNYVBhODA3MTEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmUxOWI0MGMyZTYxZGJhNDI4N2ZlZmFiMmRkZTAxNDVhZWUzYjQwOTZmYzAzZjNjYzg0YTM4NTllYmRlNjJkZiIKICAgIH0KICB9Cn0=",
            "GGRW7hVdqxJ4yrqkmYTq6XjL6IAoVs/rDyPXkO5M/BnBCcN5wI6TjlCpKfr7Jv2tFPFQYUTou29ERTk8mviAGhXBZAs2bmOHlZpWS2st2cKw6c3Dhb9qV0/xB8BG+rdnfdCdzcGw6V+gOE1as89RFq6xXd5pcI14pj/CYmS7h1OAu38s28iOHqd2MRPGW2EGXbOzZAGBCeHhnipL4GJCGjIEVutIZFkhz2nlbL8Qe7vIEC6c9Giv+7z7agKdQ4KIzc1Q9pDnht9jXlZ8pSGrPQthviBud+1JWvyyOmDh9GctifWkw2mXU91cQ/PlrNUW8rcKI+fVDNhxm/wI5rx/YByOLV1kJ1K4VlB/sKUM7wTDrvd4ayXhvFCAB9i1kRisue5kqaA+FSc/n5f8WoxZYPwunFDeIFD99mJxQeS512WbufWeTDbxmfcXkFwn9bLUYnJ38pGNZFOxh76NWASLI5eMoN9hceO9K38rLtF0Amnh6Nd5DAB2Raylbg3X2+vxRrbeX9S9yZPdq8kE+7KcFZUF6pkNbpa5IM/tbWkJkkiGZUT0QT6pXY3wljm9qgPc/8QfzfuLH7thqxBcsWoKalcgLaQFjPu36ZsmSXhE5TBQTUd4rbZPEcsPXv4JcFGwl/bmq9OykCubr18rqanbOdhwmZspgIALwclDSnBNN6U=")),

        STUMBLE_GUY(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTYwMDU4ODE2NDEwMiwKICAicHJvZmlsZUlkIiA6ICJhYTZhNDA5NjU4YTk0MDIwYmU3OGQwN2JkMzVlNTg5MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiejE0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzIzODMxMjhkYzc0ODM2OGJkYWVmZjkyNDEyMDg3ODNlNmQ2ZmQ5YmJjZmMxYjk3OWVlZDNjMTZiY2RlOTI1ODkiCiAgICB9CiAgfQp9",
        "FgbH2ikvfoDBi/VKqsVx0pLA6OnB/lrGVZG8qfeuNfmE8Ln/HQ8z+rU+8kYpoXJuIsoJlEOCSxCVWMy1dorHnYflMYyKZ6EYflVhOAwJkXpHlC71zN82/DhEKuH0V4LeRYw8ehODe2DAH5+kYbjCFoCd+vuNJvwQqhvpiMG1PUKzIEnDtwDMsptjPCZSo4mUa+bBjHv98mEL4XUmOq4IDRQRtzsN6HYgrx7AcJwkwStufXQ3hrU0yqGYSU83lSUx0P6YFQ6Cp0WSrda6NF475z93Cpxbt8GRSbP+d6qu1gdSm5K6F2Y3/BJQsR8r1pRLcEXlzMJyhwi3ckwC8Oi6chOOkYbHv8/tISdZ7jcMax9jaPSOnw9F1ohRZp8gyKKjhRQy2T6KNJSLffgotZ5DKH1ByhHFmyBnz6XwQZlMTCTtUQT5ExUuJ1L0ca/Q/AWYOPZybXHU+RDav05URXGHoBcDvsZShuHXEmmLuPvNKA8wNLcJvMpee1a3n35EY1QcGBcsRqRsvhq2C2LfHNg0xcWTnmZ/03v1hyTmq418VnwH0xZlFi0/bK7/sJ+9jebaUj6ipqwTHj7gS6gOgZYIQQPVcs5y5KXtR1bjl4LBgviiu9AlGWVmMMLT9pYs6OOqhPiGe4rfQ6LGrdMdVngk3f8CqPDovC/qG154P3J0XxE=")),

        MINDECRAFTES(PlayerSkin("ewogICJ0aW1lc3RhbXAiIDogMTY1ODYyMTU4MjU2NywKICAicHJvZmlsZUlkIiA6ICIzOTdlMmY5OTAyNmI0NjI1OTcyNTM1OTNjODgyZjRmMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJ4WnlkdWVMeCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yYzAwOThjYjMyNGYxNGZmNTQ3N2QwZDA3NGNkZjVjMjc4Yjk0NmI5MjQyNjViNmRkNDljNmFkNDFmMTA1YTYxIgogICAgfQogIH0KfQ==",
        "W8LT6UyN90VdflXZG9gGnzLdsLd9A6z5ROvfzbNWBcrfN1qHuwqmqxPnWYknwuGI9QL24tvTUlsZI8TwvLe/gcz4HUUUzLtgjHjsS11Pr4QabVfJ8kgQgtF63TaqQw0udIFCPhNiaPkCQVNLnBYKfNlry1WrUnrZK0jt3hWvx65Yh93hwxjpw/dRaE3baXgWVj+1zLWT3X3DcnqqvETFboenG6rJvzkcqN4xrk75/DnaZg1fal3KHtTlDnzShvNdFSnTU98phOMWkkUeyCJk9nOOuXY0OIbHVNVNBH1p2mI9FxTrJKqM+8FooFLTksLvU+9McMiB3sYdOrg4LrLx0d6x0uKYD7FZR5FtQ1nKsgY8e1Y+bYoeT8oEO/Dvl2slruWlg475S3etnryS5htAC6Ngfishvbj0bd2c5xgUQzLjw58Ff2OIZAt8YAV1CzidRzJGfAm6PIN+j/O2agHmu+tIpsdKncDoIzE/jQh3sV+sG+kNdqJMR0KrD/cumM9XtpvjjhI+yn/meyh5c8ufq+hD4xBnTu13ZDGmfWhCzjVnMMJSJRBuS5qlHh+1ugSiMdQJ0+FnEMpRa3Vy7sOJeHme0c3LkGrRPonVBevoEqpOjzDaUYamutkVhcBZ0RR4lCDPI0MCqR8fN4AcLQTG3hD1hXZbXyOEjElFsGJlkQI=")),
    }
}