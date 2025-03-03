package com.bluedragonmc.server.command

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.model.PlayerDocument
import com.bluedragonmc.server.service.Database
import kotlinx.coroutines.runBlocking
import net.minestom.server.MinecraftServer
import net.minestom.server.command.ArgumentParserType
import net.minestom.server.command.CommandSender
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
import net.minestom.server.network.NetworkBuffer
import net.minestom.server.utils.entity.EntityFinder
import net.minestom.server.utils.location.RelativeVec
import java.util.UUID
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
                SuggestionEntry(it.uuid.toString())
            }.filter { it.entry.startsWith(suggestion.input) })
        }
    }

    override fun parse(sender: CommandSender, input: String): Instance {
        val uuid = backingArgument.parse(sender, input)
        return MinecraftServer.getInstanceManager().getInstance(uuid)
            ?: throw ArgumentSyntaxException("Instance not found", uuid.toString(), INVALID_INSTANCE)
    }

    override fun parser(): ArgumentParserType = backingArgument.parser()
}

class ArgumentGameId(id: String) : Argument<Game>(id) {
    private val backingArgument = ArgumentString(id)

    companion object {
        private const val INVALID_GAME_ID = -1 // Arbitrary error code, see ArgumentSyntaxException
    }

    init {
        setSuggestionCallback { _, _, suggestion ->
            suggestion.entries.addAll(Game.games.map {
                SuggestionEntry(it.id)
            }.filter { it.entry.startsWith(suggestion.input) })
        }
    }

    override fun parse(sender: CommandSender, input: String): Game {
        val gameId = backingArgument.parse(sender, input)
        return Game.findGame(gameId)
            ?: throw ArgumentSyntaxException("Game not found", gameId, INVALID_GAME_ID)
    }

    override fun nodeProperties(): ByteArray {
        return NetworkBuffer.makeArray { packetWriter: NetworkBuffer ->
            packetWriter.write(NetworkBuffer.VAR_INT, 0) // Single word
        }
    }

    override fun parser(): ArgumentParserType = backingArgument.parser()
}

/**
 * An argument that returns a [PlayerDocument] for a player that could possibly be offline.
 * Online players are suggested to the player, but offline players can be passed to this argument
 * and properly converted into a PlayerDocument.
 */
class ArgumentOfflinePlayer(id: String) : Argument<PlayerDocument>(id) {

    override fun parse(sender: CommandSender, input: String): PlayerDocument {
        val doc: PlayerDocument?
        runBlocking {
            doc = try {
                // If the input is a UUID, use that to look up the player. If not, consider the input a username.
                Database.connection.getPlayerDocument(UUID.fromString(input))
            } catch (_: IllegalArgumentException) {
                Database.connection.getPlayerDocument(input)
            }
        }
        if (doc == null) throw ArgumentSyntaxException("Offline player not found", input, -1)
        return doc
    }

    override fun parser(): ArgumentParserType = ArgumentParserType.STRING

    init {
        setSuggestionCallback { _, _, suggestion ->
            suggestion.entries.addAll(MinecraftServer.getConnectionManager().onlinePlayers.filter {
                suggestion.input.isEmpty() || it.username.startsWith(suggestion.input)
            }.map { SuggestionEntry(it.username) })
        }
    }

    override fun nodeProperties(): ByteArray {
        return NetworkBuffer.makeArray { packetWriter: NetworkBuffer ->
            packetWriter.write(NetworkBuffer.VAR_INT, 0) // Single word
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

    override fun parse(sender: CommandSender, input: String) = backingArgument.parse(sender, input)

    override fun parser(): ArgumentParserType = ArgumentParserType.STRING

    init {
        setSuggestionCallback { _, _, suggestion ->
            suggestion.entries.addAll(MinecraftServer.getConnectionManager().onlinePlayers.filter {
                suggestion.input.isEmpty() || it.username.startsWith(suggestion.input)
            }.map { SuggestionEntry(it.username) })
        }
    }

    override fun nodeProperties(): ByteArray {
        return NetworkBuffer.makeArray { packetWriter: NetworkBuffer ->
            packetWriter.write(NetworkBuffer.VAR_INT, 0) // Single word
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
object StringArgument : ArgumentTypeDelegation<String>(::ArgumentString)
object GameArgument : ArgumentTypeDelegation<Game>(::ArgumentGameId)
object PlayerArgument : ArgumentTypeDelegation<EntityFinder>(::ArgumentPlayer)
object OfflinePlayerArgument : ArgumentTypeDelegation<PlayerDocument>(::ArgumentOfflinePlayer)
object OptionalPlayerArgument : ArgumentTypeDelegation<String>(::ArgumentOptionalPlayer)
object InstanceArgument : ArgumentTypeDelegation<Instance>(::ArgumentInstance)