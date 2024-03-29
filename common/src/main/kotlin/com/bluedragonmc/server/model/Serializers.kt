@file:OptIn(ExperimentalSerializationApi::class)

package com.bluedragonmc.server.model

import com.bluedragonmc.server.utils.miniMessage
import com.github.jershell.kbson.FlexibleDecoder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.coordinate.Pos
import org.bson.BsonType
import java.util.*

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
        return when (decoder.reader.currentBsonType) {
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

    override val descriptor: SerialDescriptor = LenientDoubleArraySerializer.descriptor
    override fun deserialize(decoder: Decoder): Pos = decoder.decodeSerializableValue(delegateSerializer).let {
        Pos(it[0], it[1], it[2], it[3].toFloat(), it[4].toFloat())
    }

    override fun serialize(encoder: Encoder, value: Pos) = encoder.encodeSerializableValue(
        delegateSerializer,
        arrayOf(value.x, value.y, value.z, value.yaw.toDouble(), value.pitch.toDouble())
    )
}

object ComponentSerializer : KSerializer<Component> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Component", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Component =
        miniMessage.deserialize(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Component) =
        encoder.encodeString(miniMessage.serialize(value))
}

object TextColorSerializer : KSerializer<TextColor> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TextColor", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TextColor =
        TextColor.fromHexString(decoder.decodeString())!!

    override fun serialize(encoder: Encoder, value: TextColor) =
        encoder.encodeString(value.asHexString())
}