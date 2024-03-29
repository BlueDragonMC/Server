package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.SoftDependsOn
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import java.util.function.Predicate
import kotlin.test.*

class ModuleHolderTest {

    private lateinit var instance: ModuleHolder

    private class SimpleModuleHolder : ModuleHolder() {
        override fun <T : GameModule> register(module: T, filter: Predicate<Event>) {}
    }

    private open class GameModuleStub : GameModule() {
        override fun initialize(parent: Game, eventNode: EventNode<Event>) {}
    }

    private open class SimpleGameModule : GameModuleStub()

    @DependsOn(SimpleGameModule::class)
    private class SimpleDependent : GameModuleStub()

    @DependsOn(SimpleGameModule::class)
    private class OtherSimpleDependent : GameModuleStub()

    @SoftDependsOn(SimpleGameModule::class)
    private class SimpleSoftDependent : GameModuleStub()

    @DependsOn(SimpleDependent::class)
    private class MultiLevelDependent : GameModuleStub()

    @DependsOn(SimpleDependent::class, OtherSimpleDependent::class)
    private class MultiDependencyModule : GameModuleStub()

    @DependsOn(SelfDependency::class)
    private class SelfDependency : GameModuleStub()

    @BeforeEach
    fun setup() {
        instance = spyk<SimpleModuleHolder>()
    }

    @Test
    fun `Module with no dependencies is registered`() {
        val module = SimpleGameModule()
        instance.use(module)
        assertContains(instance.modules, module)
        verify {
            instance.register(module, any())
        }
    }

    @Test
    fun `Modules registered after dependencies`() {
        val module = SimpleGameModule()
        val dependent = SimpleDependent()

        instance.use(dependent)
        assertTrue(instance.modules.isEmpty())
        verify(inverse = true) {
            instance.register(dependent, any()) // Make sure the dependent module was not registered too early
        }
        instance.use(module)
        assertContentEquals(instance.modules, listOf(module, dependent))
        verifyOrder {
            // Make sure the two modules were registered, and in the correct order
            instance.register(module, any())
            instance.register(dependent, any())
        }
    }

    @Test
    fun `Modules registered after soft dependencies`() {
        val module = SimpleGameModule()
        val dependent = SimpleSoftDependent()

        instance.use(dependent)
        assertTrue(instance.modules.isEmpty())
        verify(inverse = true) {
            instance.register(dependent, any()) // Make sure the dependent module was not registered too early
        }
        instance.use(module)
        assertContentEquals(instance.modules, listOf(module, dependent))
        verifyOrder {
            // Make sure the two modules were registered, and in the correct order
            instance.register(module, any())
            instance.register(dependent, any())
        }
    }

    @Test
    fun `Modules registered even if soft dependencies are absent`() {
        val dependent = SimpleSoftDependent()

        instance.use(dependent)
        assertTrue(instance.modules.isEmpty())
        verify(inverse = true) {
            instance.register(dependent, any()) // Make sure the dependent module was not registered too early
        }
        instance.checkUnmetDependencies()
        assertContentEquals(instance.modules, listOf(dependent))
        verify {
            // Make sure the module was registered
            instance.register(dependent, any())
        }
    }

    @Test
    fun `Unsatisfied dependencies throw`() {
        val module = SimpleGameModule()
        val dependent = SimpleDependent()

        instance.use(dependent)
        assertThrows<IllegalStateException> {
            instance.checkUnmetDependencies()
        }
        instance.use(module)
        assertDoesNotThrow {
            instance.checkUnmetDependencies()
        }
    }

    @Test
    fun `Module registration order`() {
        val first = SimpleGameModule()
        val second = SimpleDependent()
        val third = MultiLevelDependent()

        // Use the modules in reverse order
        instance.use(third)
        instance.use(second)
        instance.use(first)

        verifyOrder {
            // Make sure all modules were registered after their dependencies
            instance.register(first, any())
            instance.register(second, any())
            instance.register(third, any())
        }
    }

    @Test
    fun `Multiple module dependents`() {
        val module = SimpleGameModule()
        val dependent1 = SimpleDependent()
        val dependent2 = OtherSimpleDependent()

        // Use multiple modules dependent on the same module type
        instance.use(dependent1)
        instance.use(dependent2)
        instance.use(module)

        // Make sure they were initialized in the correct order
        verifyOrder {
            instance.register(module, any())
            instance.register(dependent1, any())
            instance.register(dependent2, any())
        }
    }

    @Test
    fun `Multiple module dependencies`() {
        val root = SimpleGameModule()
        val dependent1 = SimpleDependent()
        val dependent2 = OtherSimpleDependent()
        val multiDependency = MultiDependencyModule()

        // Use multiple modules to create a complex dependency tree
        instance.use(root) // no dependencies
        instance.use(multiDependency) // depends on dependent1 and dependent2
        instance.use(dependent1) // depends on root
        instance.use(dependent2) // depends on root

        // Ensure multiDependency was registered last, after BOTH of its dependencies have been initialized
        verifyOrder {
            instance.register(root, any())
            instance.register(dependent1, any())
            instance.register(dependent2, any())
            instance.register(multiDependency, any())
        }
    }

    @Test
    fun `Register module twice`() {
        val module = SimpleGameModule()

        instance.use(module)
        assertContentEquals(instance.modules, listOf(module))
        instance.use(module)
        assertContentEquals(instance.modules, listOf(module))
        assertEquals(instance.modules.size, 1)
    }

    @Test
    fun `Self-dependency throws`() {
        val module = SelfDependency()

        assertThrows<IllegalStateException> {
            instance.use(module)
        }
    }

    @Test
    fun getModule() {
        val module = SimpleGameModule()

        assertThrows<IllegalStateException> {
            instance.getModule<SimpleGameModule>()
        }
        assertFalse(instance.hasModule<SimpleGameModule>())

        instance.use(module)

        assertDoesNotThrow {
            instance.getModule<SimpleGameModule>()
        }
        assertTrue(instance.hasModule<SimpleGameModule>())
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

}