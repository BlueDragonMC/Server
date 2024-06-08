@file:OptIn(ExperimentalSerializationApi::class)

package com.bluedragonmc.server.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

open class ToStringSerializer<T>(
    descriptorName: String,
    private val toStringMethod: (T) -> String,
    private val fromStringMethod: (String) -> T,
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
