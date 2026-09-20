package com.bluedragonmc.server.module

import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.utils.GameState
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

abstract class GameModule {

    open val eventPriority = 0

    lateinit var eventNode: EventNode<Event>

    /**
     * The [ModuleHolder] that this module was registered with.
     * Set by [ModuleHolder.register] before [initialize] is called.
     */
    @PublishedApi
    internal lateinit var holder: ModuleHolder
        private set

    internal fun attach(holder: ModuleHolder) {
        this.holder = holder
    }

    abstract fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>)
    open fun deinitialize() {}

    val logger: Logger by lazy {
        LoggerFactory.getLogger(javaClass)
    }

    open fun getRequiredDependencies(): Array<out KClass<out GameModule>> =
        javaClass.getAnnotation(DependsOn::class.java)?.dependencies ?: emptyArray<KClass<out GameModule>>()

    open fun getSoftDependencies(): Array<out KClass<out GameModule>> =
        javaClass.getAnnotation(SoftDependsOn::class.java)?.dependencies ?: emptyArray<KClass<out GameModule>>()

    fun getDependencies(): Array<out KClass<out GameModule>> =
        arrayOf(*getRequiredDependencies(), *getSoftDependencies())

    /**
     * Finds a module that this module has declared in [DependsOn] or [SoftDependsOn].
     *
     * @throws IllegalStateException if this module does not declare the requested type, or the module is not loaded.
     */
    inline fun <reified T : GameModule> getModule(): T {
        checkAccessible(T::class, T::class.isInstance(this))
        return holder.requireModule(T::class)
    }

    /**
     * Finds a module that this module has declared in [DependsOn] or [SoftDependsOn], or returns `null` if it is
     * not loaded.
     *
     * @throws IllegalStateException if this module does not declare the requested type.
     */
    inline fun <reified T : GameModule> getModuleOrNull(): T? {
        checkAccessible(T::class, T::class.isInstance(this))
        return holder.findModule(T::class)
    }

    @PublishedApi
    internal fun checkAccessible(type: KClass<out GameModule>, isSelf: Boolean) {
        val accessible = isSelf || getDependencies().any { it.java.isAssignableFrom(type.java) }
        if (!accessible) {
            error(
                "${this::class.simpleName} accessed ${type.simpleName} " +
                    "without declaring it in @DependsOn or @SoftDependsOn"
            )
        }
    }

    /** The players in this module's game. Requires [PlayerListModule]. */
    open val players: List<Player> get() = getModule<PlayerListModule>().players

    /** The audience of this module's game. Requires [PlayerListModule]. */
    open val audience: PacketGroupingAudience get() = getModule<PlayerListModule>()

    /** The state of this module's game. Requires [GameStateModule]. */
    open var state: GameState
        get() = getModule<GameStateModule>().state
        set(value) {
            getModule<GameStateModule>().state = value
        }

    /** The unique identifier of this module's game. Requires [GameInfoModule]. */
    open val id: String get() = getModule<GameInfoModule>().id

    /** The metadata of this module's game. Requires [GameInfoModule]. */
    open val data: GameData get() = getModule<GameInfoModule>().data

}
