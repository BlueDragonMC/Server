package com.bluedragonmc.server.database

import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.PermissionGroup
import io.mockk.coEvery
import io.mockk.coVerifyAll
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertContentEquals

class DatabaseModuleTest {

    @BeforeEach
    fun setup() {
        mockkObject(DatabaseModule.Companion)
    }

    @Test
    fun `Recursively get group permissions`() {

        coEvery { DatabaseModule.getGroupByName(any()) } answers {
            val name = it.invocation.args[0]!! as String
            PermissionGroup(name, permissions = mutableListOf("permission.$name"))
        }

        val parent = PermissionGroup("parent", inheritsFrom = listOf("child1", "child2", "child3"))
        val permissions = assertDoesNotThrow {
            runBlocking { parent.getAllPermissions() }
        }

        assertContentEquals(permissions, listOf("permission.child1", "permission.child2", "permission.child3"))

        coVerifyAll {
            DatabaseModule.getGroupByName("child1")
            DatabaseModule.getGroupByName("child2")
            DatabaseModule.getGroupByName("child3")
        }
    }

    @AfterEach
    fun destroy() {
        unmockkAll()
    }

}