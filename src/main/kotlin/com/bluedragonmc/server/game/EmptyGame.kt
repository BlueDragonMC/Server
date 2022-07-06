package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.ai.MLAgentModule
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import com.bluedragonmc.server.module.instance.InstanceModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

class EmptyGame : Game("Testing") {
    init {
        use(object : InstanceModule() {
            private val instance by lazy {
                MinecraftServer.getInstanceManager().createInstanceContainer().apply {
                    setGenerator { unit ->
                        unit.modifier().fillHeight(0, 1, Block.MOSS_BLOCK)
                    }
                    setBlock(0, 1, 0, Block.REDSTONE_BLOCK)
                }
            }

            override fun getInstance(): Instance = instance
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {}
        })
        use(MLAgentModule())
        use(SpawnpointModule(
            SpawnpointModule.TestSpawnpointProvider(Pos(0.0, 4.0, 0.0))
        ))
        use(InstantRespawnModule())
        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
                    logger.info("Player spawned: ${event.player}")
                    event.player.isFlying = true
                }
            }
        })
        ready()
    }
}
