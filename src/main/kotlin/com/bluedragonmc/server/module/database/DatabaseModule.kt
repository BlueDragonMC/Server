@file:OptIn(ExperimentalSerializationApi::class)

package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.mongoHostname
import com.github.jershell.kbson.FlexibleDecoder
import com.mongodb.ConnectionString
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.permission.Permission
import org.bson.BsonType
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import java.util.*
import kotlin.coroutines.CoroutineContext

class DatabaseModule : GameModule() {

    companion object {

        val IO = object : CoroutineScope {
            override val coroutineContext: CoroutineContext =
                Dispatchers.IO + SupervisorJob() + CoroutineName("Database IO")
        }

        private val client: CoroutineClient by lazy {
            KMongo.createClient(ConnectionString("mongodb://$mongoHostname")).coroutine
        }
        val database: CoroutineDatabase by lazy {
            client.getDatabase("bluedragon")
        }

        internal fun getPlayersCollection(): CoroutineCollection<PlayerDocument> = database.getCollection("players")
        internal fun getGroupsCollection(): CoroutineCollection<PermissionGroup> = database.getCollection("groups")
        internal fun getMapsCollection(): CoroutineCollection<MapData> = database.getCollection("maps")
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            // Load players' data from the database when they spawn
            val player = event.player as CustomPlayer
            if (!player.isDataInitialized()) IO.launch {
                player.data = getPlayerDocument(player)
                logger.info("Loaded player data for ${player.username}")
            }
        }
    }

    suspend fun getPlayerDocument(player: Player): PlayerDocument {
        val col = getPlayersCollection()
        val foundDocument = col.findOneById(player.uuid.toString())
        return if (foundDocument == null) {
            // Create a document if it doesn't exist
            val newDocument = PlayerDocument(uuid = player.uuid)
            col.insertOne(newDocument)
            newDocument
        } else {
            foundDocument
        }
    }

    suspend fun getMap(mapName: String): MapData {
        val col = getMapsCollection()
        return col.findOneById(mapName)!!
    }

    open class ToStringSerializer<T>(
        descriptorName: String,
        private inline val toStringMethod: (T) -> String,
        private inline val fromStringMethod: (String) -> T,
    ) : KSerializer<T> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(descriptorName, PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): T = fromStringMethod(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: T) {
            encoder.encodeString(toStringMethod(value))
        }
    }

    object UUIDSerializer : ToStringSerializer<UUID>("UUID", UUID::toString, UUID::fromString)
    object PermissionSerializer :
        ToStringSerializer<Permission>("Permission", Permission::getPermissionName, ::Permission)

    object DateSerializer : KSerializer<Date> {
        override val descriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.LONG)
        override fun serialize(encoder: Encoder, value: Date) = encoder.encodeLong(value.time)
        override fun deserialize(decoder: Decoder): Date = Date(decoder.decodeLong())
    }

    object LenientDoubleArraySerializer : KSerializer<Array<Double>> {

        val s = ListSerializer(LenientDoubleSerializer)

        override val descriptor: SerialDescriptor
            get() = SerialDescriptor("bluedragon.DoubleArray", s.descriptor)

        override fun deserialize(decoder: Decoder): Array<Double> {
            return s.deserialize(decoder).toTypedArray()
        }

        override fun serialize(encoder: Encoder, value: Array<Double>) {
            s.serialize(encoder, value.toList())
        }

    }

    object LenientDoubleSerializer : KSerializer<Double> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("bluedragon.Double", PrimitiveKind.DOUBLE)

        override fun deserialize(decoder: Decoder): Double {
            decoder as FlexibleDecoder
            return when(decoder.reader.currentBsonType) {
                BsonType.INT32 -> decoder.reader.readInt32().toDouble()
                BsonType.DOUBLE -> decoder.reader.readDouble()
                else -> throw SerializationException("Invalid BSON type: ${decoder.reader.currentBsonType}")
            }
        }

        override fun serialize(encoder: Encoder, value: Double) {
            encoder.encodeDouble(value)
        }
    }

    object PosSerializer : KSerializer<Pos> {
        private val delegateSerializer = LenientDoubleArraySerializer

        override val descriptor: SerialDescriptor = delegateSerializer.descriptor
        override fun deserialize(decoder: Decoder): Pos = decoder.decodeSerializableValue(delegateSerializer).let {
            Pos(it[0], it[1], it[2], it[3].toFloat(), it[4].toFloat())
        }

        override fun serialize(encoder: Encoder, value: Pos) =
            encoder.encodeSerializableValue(delegateSerializer, arrayOf(value.x, value.y, value.z, value.yaw.toDouble(), value.pitch.toDouble()))
    }

}