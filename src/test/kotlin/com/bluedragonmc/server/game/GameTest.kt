package com.bluedragonmc.server.game

import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.queue.LocalTestingEnvironment
import com.bluedragonmc.server.queue.games
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class GameTest {

    private lateinit var environment: Environment

    @BeforeEach
    fun setup() {
        environment = LocalTestingEnvironment()
    }

    @Test
    fun `All games can initialize`() {
        for ((_, ctor) in games) {
            assertDoesNotThrow {
                val instance = ctor.invoke("mock")
            }
        }
    }

}