package com.bluedragonmc.server.module.config.serializer

import com.bluedragonmc.server.module.config.ConfigModule
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.BasicConfigurationNode
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.gson.GsonConfigurationLoader
import kotlin.test.assertEquals

class PointSerializerTest {

    private val builder = GsonConfigurationLoader
        .builder()
        .defaultOptions(ConfigModule.SERIALIZATION_OPTIONS)

    private fun node() = BasicConfigurationNode.root(ConfigModule.SERIALIZATION_OPTIONS)
    private fun save(node: ConfigurationNode) = builder.buildAndSaveString(node)
    private fun load(json: String) = builder.buildAndLoadString(json)

    @Test
    fun testPointSerializer() {
        val testNode = node()
        testNode.node("pos").set(Pos(0.0, 64.0, 0.0))
        testNode.node("vec").set(Vec(0.0, 65.0, 0.0))
        testNode.node("blockvec").set(BlockVec(0.0, 66.0, 0.0))

        // Save the node & parse it
        val str = save(testNode)
        val parsed = load(str)

        // When deserializing, everything is returned as a Pos
        assertEquals(Pos(0.0, 64.0, 0.0), parsed.node("pos").get(Point::class.java))
        assertEquals(Pos(0.0, 65.0, 0.0), parsed.node("vec").get(Point::class.java))
        assertEquals(Pos(0.0, 66.0, 0.0), parsed.node("blockvec").get(Point::class.java))

        // Make sure get<Pos>() works
        assertEquals(Pos(0.0, 66.0, 0.0), parsed.node("blockvec").get(Pos::class.java))
    }
}