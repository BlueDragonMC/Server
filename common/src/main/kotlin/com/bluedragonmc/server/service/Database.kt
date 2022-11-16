package com.bluedragonmc.server.service

import com.bluedragonmc.server.api.DatabaseConnection
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

object Database {

    lateinit var connection: DatabaseConnection
        private set

    fun initialize(connection: DatabaseConnection) {
        Database.connection = connection
    }

    val IO = object : CoroutineScope {
        override val coroutineContext: CoroutineContext =
            Dispatchers.IO + SupervisorJob() + CoroutineName("Database IO")
    }
}

