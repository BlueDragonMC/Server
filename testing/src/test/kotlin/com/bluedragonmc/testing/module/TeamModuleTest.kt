package com.bluedragonmc.testing.module

import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.testing.utils.TestUtils
import net.kyori.adventure.text.TranslatableComponent
import net.minestom.server.api.Env
import net.minestom.server.api.EnvTest
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.ChatMessagePacket
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.network.packet.server.play.TeamsPacket.AddEntitiesToTeamAction
import net.minestom.server.network.packet.server.play.TeamsPacket.CreateTeamAction
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnvTest
class TeamModuleTest {

    private fun setup(env: Env, module: TeamModule, playerCount: Int): List<Player> {
        val game = TestUtils.emptyGame(env, module)
        // Create 10 players and wait for them all to spawn
        val players = (0 until playerCount).map {
            env.createPlayer(game.getInstance(), Pos.ZERO)
        }
        // Wait for them all to spawn, then add them to the game
        players.forEachIndexed { index, player ->
            player.setUsernameField("Player${index + 1}")
            TestUtils.waitForSpawn(env, player)
            game.addPlayer(player)
        }
        TestUtils.startGame(game)
        return players
    }

    @Test
    fun `Auto teams with PLAYER_COUNT strategy`(env: Env) {
        for (i in 2..5) {
            val module = TeamModule(autoTeams = true, autoTeamMode = TeamModule.AutoTeamMode.PLAYER_COUNT, autoTeamCount = i)
            val players = setup(env, module, 10)
            players.forEach {
                val team = module.getTeam(it)
                assertNotNull(team, "Player must be assigned a team.")
                assertTrue(team.players.size <= i, "Each team must have $i players or fewer.")
            }
        }
    }

    @Test
    fun `Auto teams with TEAM_COUNT strategy`(env: Env) {
        for (i in 2..20) {
            val module = TeamModule(autoTeams = true, autoTeamMode = TeamModule.AutoTeamMode.TEAM_COUNT, autoTeamCount = i)
            val players = setup(env, module, 20)
            val distinct = players.map {
                module.getTeam(it).also { team -> assertNotNull(team, "Player ${it.username} has null team") }
            }.distinct()
            assertEquals(i, distinct.size, "Actual team count must equal the desired team count.")

            val minCount = distinct.minOf { it!!.players.size }
            val maxCount = distinct.maxOf { it!!.players.size }

            assertTrue(abs(minCount - maxCount) <= 1, "Teams must be balanced. " +
                    "Teams must always have within one player of each other. The smallest team has $minCount " +
                    "players, and the largest team has $maxCount players. The difference must be one or zero.")
        }
    }

    @Test
    fun `Team assignment packets`(env: Env) {
        // Create a game with a TeamModule
        val module = TeamModule(autoTeams = true, autoTeamMode = TeamModule.AutoTeamMode.TEAM_COUNT)
        val game = TestUtils.emptyGame(env, module)

        // Create a player and wait for them to spawn
        val conn = env.createConnection()
        val player = conn.connect(game.getInstance(), Pos.ZERO).join()
        TestUtils.waitForSpawn(env, player)
        game.addPlayer(player)

        // Track incoming chat packets
        val chat = conn.trackIncoming(ChatMessagePacket::class.java)
        val teams = conn.trackIncoming(TeamsPacket::class.java)

        TestUtils.startGame(game)

        // The module should have sent one packet with the team assignment
        chat.assertSingle {
            assertTrue(it.message is TranslatableComponent, "Message is not TranslatableComponent")
            assertEquals((it.message as TranslatableComponent).key(), "module.team.assignment", "Incorrect translation key")
        }
        val teamsPackets = teams.collect()
        assertEquals(3, teamsPackets.size, "Incorrect amount of teams packets received.")
        assertEquals(1, teamsPackets.count { it.action is AddEntitiesToTeamAction }, "AddEntityToTeamAction must be sent once.")
        assertTrue(teamsPackets.first().action is CreateTeamAction, "First teams packet must create a team.")
    }

//    @Test
//    fun `Team friendly fire`(env: Env) {
//        // Create a game with a TeamModule
//        val teamsModule = TeamModule(autoTeams = false, allowFriendlyFire = false)
//        val combatModule = spyk(OldCombatModule())
//        val game = TestUtils.emptyGame(env, teamsModule, combatModule)
//
//        run {
//            // Create two players and add them to a team which allows friendly fire
//            val player1 = TestUtils.addPlayer(env, game)
//            val player2 = TestUtils.addPlayer(env, game)
//            val allowedTeam = TeamModule.Team(allowFriendlyFire = true)
//            allowedTeam.addPlayer(player1)
//            allowedTeam.addPlayer(player2)
//            player1.addPacketToQueue(
//                ClientInteractEntityPacket(player2.entityId, ClientInteractEntityPacket.Attack(), false)
//            )
//            player1.interpretPacketQueue()
//        }
//    }

}