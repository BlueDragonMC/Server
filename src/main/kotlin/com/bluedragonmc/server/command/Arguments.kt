package com.bluedragonmc.server.command

import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.NodeMaker
import net.minestom.server.command.builder.arguments.*
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity
import net.minestom.server.command.builder.arguments.minecraft.ArgumentUUID
import net.minestom.server.command.builder.arguments.minecraft.registry.ArgumentEntityType
import net.minestom.server.command.builder.arguments.number.ArgumentInteger
import net.minestom.server.command.builder.arguments.relative.ArgumentRelativeBlockPosition
import net.minestom.server.command.builder.exception.ArgumentSyntaxException
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
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
                SuggestionEntry(it.uniqueId.toString(), Component.text(it::class.simpleName ?: "Unknown"))
            }.filter { it.entry.startsWith(suggestion.input) })
        }
    }

    override fun parse(input: String): Instance {
        val uuid = backingArgument.parse(input)
        return MinecraftServer.getInstanceManager().getInstance(uuid) ?: throw ArgumentSyntaxException("Instance not found", uuid.toString(), INVALID_INSTANCE)
    }

    override fun processNodes(nodeMaker: NodeMaker, executable: Boolean) {
        backingArgument.processNodes(nodeMaker, executable)
    }
}

object LiteralArgument : ArgumentTypeDelegation<String>(::ArgumentLiteral)
object IntArgument : ArgumentTypeDelegation<Int>(::ArgumentInteger)
object StringArgument : ArgumentTypeDelegation<String>(::ArgumentString)
object StringArrayArgument : ArgumentTypeDelegation<Array<String>>(::ArgumentStringArray)
object BlockPosArgument : ArgumentTypeDelegation<RelativeVec>(::ArgumentRelativeBlockPosition)
object EntityTypeArgument : ArgumentTypeDelegation<EntityType>(::ArgumentEntityType)
object EntitySelectorArgument : ArgumentTypeDelegation<EntityFinder>(::ArgumentEntity)
object WordArgument : ArgumentTypeDelegation<String>(::ArgumentWord)
object PlayerArgument : ArgumentTypeDelegation<EntityFinder>(::ArgumentPlayer)
object InstanceArgument : ArgumentTypeDelegation<Instance>(::ArgumentInstance)