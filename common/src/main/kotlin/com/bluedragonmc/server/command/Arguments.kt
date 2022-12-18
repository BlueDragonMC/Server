package com.bluedragonmc.server.command

import com.bluedragonmc.server.model.PlayerDocument
import com.bluedragonmc.server.service.Database
import kotlinx.coroutines.runBlocking
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.arguments.*
import net.minestom.server.command.builder.arguments.minecraft.ArgumentBlockState
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity
import net.minestom.server.command.builder.arguments.minecraft.ArgumentItemStack
import net.minestom.server.command.builder.arguments.minecraft.ArgumentUUID
import net.minestom.server.command.builder.arguments.number.ArgumentInteger
import net.minestom.server.command.builder.arguments.relative.ArgumentRelativeBlockPosition
import net.minestom.server.command.builder.exception.ArgumentSyntaxException
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.utils.binary.BinaryWriter
import net.minestom.server.utils.entity.EntityFinder
import net.minestom.server.utils.location.RelativeVec
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

open class ArgumentTypeDelegation<T : Any>(private val block: (property: KProperty<*>) -> Argument<T>) {

    constructor(constructor: KFunction<Argument<T>>) : this({ property -> constructor.call(getPropertyName(property)) })

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = block(property)

    companion object {
        private fun getPropertyName(property: KProperty<*>): String {
            val name = property.name
            return if (name.endsWith("Argument")) name.substringBefore("Argument")
            else name
        }
    }
}

class ArgumentPlayer(id: String) : ArgumentEntity(id) {
    init {
        onlyPlayers(true)
    }
}

class ArgumentInstance(id: String) : Argument<Instance>(id) {
    private val backingArgument = ArgumentUUID(id)

    companion object {
        private const val INVALID_INSTANCE = -1 // Arbitrary error code, see ArgumentSyntaxException
    }

    init {
        setSuggestionCallback { _, _, suggestion ->
            suggestion.entries.addAll(MinecraftServer.getInstanceManager().instances.map {
                SuggestionEntry(it.uniqueId.toString())
            }.filter { it.entry.startsWith(suggestion.input) })
        }
    }

    override fun parse(input: String): Instance {
        val uuid = backingArgument.parse(input)
        return MinecraftServer.getInstanceManager().getInstance(uuid)
            ?: throw ArgumentSyntaxException("Instance not found", uuid.toString(), INVALID_INSTANCE)
    }

    override fun parser(): String = backingArgument.parser()
}

/**
 * An argument that returns a [PlayerDocument] for a player that could possibly be offline.
 * Online players are suggested to the player, but offline players can be passed to this argument
 * and properly converted into a PlayerDocument.
 */
class ArgumentOfflinePlayer(id: String) : Argument<PlayerDocument>(id) {

    override fun parse(input: String): PlayerDocument {
        val doc: PlayerDocument?
        runBlocking {
            doc = Database.connection.getPlayerDocument(input)
        }
        if (doc == null) throw ArgumentSyntaxException("Offline player not found", input, -1)
        return doc
    }

    override fun parser(): String = "brigadier:string"

    init {
        setSuggestionCallback { _, _, suggestion ->
            suggestion.entries.addAll(MinecraftServer.getConnectionManager().onlinePlayers.filter {
                suggestion.input.isEmpty() || it.username.startsWith(suggestion.input)
            }.map { SuggestionEntry(it.username) })
        }
    }

    override fun nodeProperties(): ByteArray {
        return BinaryWriter.makeArray { packetWriter: BinaryWriter ->
            packetWriter.writeVarInt(0) // Single word
        }
    }
}

/**
 * An argument that returns a [String] representing a player name. The string is not validated.
 * Online players are suggested to the player, but offline players can be passed to this argument
 * and will be returned as-is.
 */
class ArgumentOptionalPlayer(id: String) : Argument<String>(id) {
    private val backingArgument = ArgumentString(id)

    override fun parse(input: String) = backingArgument.parse(input)

    override fun parser(): String = "brigadier:string"

    init {
        setSuggestionCallback { _, _, suggestion ->
            suggestion.entries.addAll(MinecraftServer.getConnectionManager().onlinePlayers.filter {
                suggestion.input.isEmpty() || it.username.startsWith(suggestion.input)
            }.map { SuggestionEntry(it.username) })
        }
    }

    override fun nodeProperties(): ByteArray {
        return BinaryWriter.makeArray { packetWriter: BinaryWriter ->
            packetWriter.writeVarInt(0) // Single word
        }
    }
}

object LiteralArgument : ArgumentTypeDelegation<String>(::ArgumentLiteral)
object IntArgument : ArgumentTypeDelegation<Int>(::ArgumentInteger)
object BooleanArgument : ArgumentTypeDelegation<Boolean>(::ArgumentBoolean)
object StringArrayArgument : ArgumentTypeDelegation<Array<String>>(::ArgumentStringArray)
object BlockPosArgument : ArgumentTypeDelegation<RelativeVec>(::ArgumentRelativeBlockPosition)
object BlockStateArgument : ArgumentTypeDelegation<Block>(::ArgumentBlockState)
object ItemStackArgument : ArgumentTypeDelegation<ItemStack>(::ArgumentItemStack)
object WordArgument : ArgumentTypeDelegation<String>(::ArgumentWord)
object PlayerArgument : ArgumentTypeDelegation<EntityFinder>(::ArgumentPlayer)
object OfflinePlayerArgument : ArgumentTypeDelegation<PlayerDocument>(::ArgumentOfflinePlayer)
object OptionalPlayerArgument : ArgumentTypeDelegation<String>(::ArgumentOptionalPlayer)
object InstanceArgument : ArgumentTypeDelegation<Instance>(::ArgumentInstance)