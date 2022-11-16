package com.bluedragonmc.server.database

import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.module.database.PermissionGroup
import com.bluedragonmc.testing.utils.DatabaseConnectionStub
import io.mockk.coEvery
import io.mockk.coVerifyAll
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertContentEquals

class DatabaseConnectionTest {

    @Test
    fun `Recursively get group permissions`() {

        val stub = mockk<DatabaseConnectionStub>()
        Database.initialize(stub)

        coEvery { stub.getGroupByName(any()) } answers {
            val name = it.invocation.args[0]!! as String
            PermissionGroup(name, permissions = mutableListOf("permission.$name"))
        }

        val parent = PermissionGroup("parent", inheritsFrom = listOf("child1", "child2", "child3"))
        val permissions = assertDoesNotThrow {
            runBlocking { parent.getAllPermissions() }
        }

        assertContentEquals(permissions, listOf("permission.child1", "permission.child2", "permission.child3"))

        coVerifyAll {
            Database.connection.getGroupByName("child1")
            Database.connection.getGroupByName("child2")
            Database.connection.getGroupByName("child3")
        }
    }

    @AfterEach
    fun destroy() {
        unmockkAll()
    }

}