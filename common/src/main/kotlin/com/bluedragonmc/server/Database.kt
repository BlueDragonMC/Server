package com.bluedragonmc.server

import com.bluedragonmc.server.api.DatabaseConnection
import com.bluedragonmc.server.impl.DatabaseConnectionImpl
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

object Database {

    lateinit var connection: DatabaseConnection
        private set

    fun initialize(connectionString: String) {
        connection = DatabaseConnectionImpl(connectionString)
    }

    fun initialize(connection: DatabaseConnection) {
        Database.connection = connection
    }

    val IO = object : CoroutineScope {
        override val coroutineContext: CoroutineContext =
            Dispatchers.IO + SupervisorJob() + CoroutineName("Database IO")
    }
}

