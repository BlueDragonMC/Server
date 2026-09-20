package com.bluedragonmc.server.game

import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.SoftDependsOn
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameModuleDependencyTest {

    private class TargetModule : GameModule() {
        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {}
    }

    private open class LookupModule : GameModule() {
        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {}

        fun lookupTarget() = getModule<TargetModule>()

        fun lookupTargetOrNull() = getModuleOrNull<TargetModule>()

        fun lookupSelf() = getModule<LookupModule>()
    }

    @DependsOn(TargetModule::class)
    private open class DeclaredModule : GameModule() {
        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {}

        fun lookupTarget() = getModule<TargetModule>()
    }

    @SoftDependsOn(TargetModule::class)
    private class SoftDeclaredModule : GameModule() {
        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {}

        fun lookupTargetOrNull() = getModuleOrNull<TargetModule>()
    }

    private class InheritedDeclaredModule : DeclaredModule()

    @Test
    fun `Undeclared module lookup throws`() {
        val holder = ModuleHolder()
        holder.use(TargetModule())
        val module = LookupModule()
        holder.use(module)

        assertFailsWith<IllegalStateException> { module.lookupTarget() }
        assertFailsWith<IllegalStateException> { module.lookupTargetOrNull() }
    }

    @Test
    fun `Declared module lookup succeeds`() {
        val holder = ModuleHolder()
        val target = TargetModule()
        holder.use(target)
        val module = DeclaredModule()
        holder.use(module)

        assertNotNull(module.lookupTarget())
    }

    @Test
    fun `Soft-declared module lookup succeeds when present`() {
        val holder = ModuleHolder()
        holder.use(TargetModule())
        val module = SoftDeclaredModule()
        holder.use(module)

        assertNotNull(module.lookupTargetOrNull())
    }

    @Test
    fun `Soft-declared module lookup returns null when absent`() {
        val holder = ModuleHolder()
        val module = SoftDeclaredModule()
        holder.use(module)
        holder.checkUnmetDependencies()

        assertNull(module.lookupTargetOrNull())
    }

    @Test
    fun `Self lookup succeeds`() {
        val holder = ModuleHolder()
        val module = LookupModule()
        holder.use(module)

        assertNotNull(module.lookupSelf())
    }

    @Test
    fun `Inherited dependency declaration is honored`() {
        val holder = ModuleHolder()
        holder.use(TargetModule())
        val module = InheritedDeclaredModule()
        holder.use(module)

        assertNotNull(module.lookupTarget())
    }
}
