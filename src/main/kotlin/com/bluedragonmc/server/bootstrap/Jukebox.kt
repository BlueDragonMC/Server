package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.api.grpc.JukeboxOuterClass
import com.bluedragonmc.api.grpc.copy
import com.bluedragonmc.api.grpc.playerSongInfo
import com.bluedragonmc.api.grpc.playerSongQueue
import com.bluedragonmc.jukebox.api.Song
import com.bluedragonmc.jukebox.impl.NBSSongLoader
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.service.Messaging
import com.google.protobuf.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventListener
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerLoadedEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.block.jukebox.JukeboxSong
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.click.Click
import net.minestom.server.item.Material
import net.minestom.server.item.component.TooltipDisplay
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import java.io.File
import java.nio.file.Paths
import java.time.Duration
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.exists
import kotlin.math.PI
import kotlin.math.pow
import kotlin.time.measureTime

object Jukebox : Bootstrap() {

    private var songs: Map<String, Song> = emptyMap()
    private val playStates = WeakHashMap<Player, SongState>()

    private val guiModule = GuiModule()

    lateinit var songSelectMenu: GuiModule.Menu
        private set

    private fun getInventoryType(): InventoryType {
        val slots = songs.size
        return when {
            slots > 46 -> InventoryType.CHEST_6_ROW
            slots > 36 -> InventoryType.CHEST_5_ROW
            slots > 27 -> InventoryType.CHEST_4_ROW
            slots > 18 -> InventoryType.CHEST_3_ROW
            slots > 9 -> InventoryType.CHEST_2_ROW
            else -> InventoryType.CHEST_1_ROW
        }
    }

    data class SongState(val song: Song, val task: Task, val tick: Int = 0)

    private val instruments = listOf(
        SoundEvent.BLOCK_NOTE_BLOCK_HARP,
        SoundEvent.BLOCK_NOTE_BLOCK_BASS,
        SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM,
        SoundEvent.BLOCK_NOTE_BLOCK_SNARE,
        SoundEvent.BLOCK_NOTE_BLOCK_HAT,
        SoundEvent.BLOCK_NOTE_BLOCK_GUITAR,
        SoundEvent.BLOCK_NOTE_BLOCK_FLUTE,
        SoundEvent.BLOCK_NOTE_BLOCK_BELL,
        SoundEvent.BLOCK_NOTE_BLOCK_CHIME,
        SoundEvent.BLOCK_NOTE_BLOCK_XYLOPHONE,
        SoundEvent.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
        SoundEvent.BLOCK_NOTE_BLOCK_COW_BELL,
        SoundEvent.BLOCK_NOTE_BLOCK_DIDGERIDOO,
        SoundEvent.BLOCK_NOTE_BLOCK_BIT,
        SoundEvent.BLOCK_NOTE_BLOCK_BANJO,
        SoundEvent.BLOCK_NOTE_BLOCK_PLING
    )

    private fun play(songName: String, player: Player, startAtTick: Int = 0) {
        logger.info("Playing song $songName for player ${player.username} starting at tick $startAtTick")
        val song = songs[songName] ?: return
        var task: Task? = null
        var currentTick = startAtTick

        task = player.scheduler().buildTask {
            val notes = try {
                song.getNotesAt(currentTick)
            } catch (e: IndexOutOfBoundsException) {
                // The song has ended
                task?.cancel()
                Messaging.IO.launch {
                    stop(player)
                    restartCurrentSong(player)
                }
                return@buildTask
            }
            notes.forEach { note ->
                val finalKey = note.key + (note.pitch ?: 0) / 100
                val volume = (note.velocity?.toFloat() ?: 100f) / 100f
                val pitch = 2f.pow((finalKey - 45) / 12f)

                if (note.pan != null && note.pan != 100.toByte()) {
                    val adjusted =
                        -(note.pan!! - 100) * (90f / 100f) * (PI / 180.0) // Convert the pan value into a rotation amount in radians
                    val direction =
                        note.getHorizontalDirection(player.position.yaw(), player.position.pitch())
                    val offset = direction.rotate(adjusted)

                    val position = Pos(
                        player.position.x() + offset.x,
                        player.position.y(),
                        player.position.z() + offset.y,
                        0f,
                        0f
                    )

                    player.playSound(
                        Sound.sound(
                            instruments[note.instrument.toInt()],
                            Sound.Source.RECORD,
                            volume,
                            pitch
                        ), position
                    )
                } else {
                    player.playSound(
                        Sound.sound(
                            instruments[note.instrument.toInt()],
                            Sound.Source.RECORD,
                            volume,
                            pitch
                        ), Sound.Emitter.self()
                    )
                }
            }
            currentTick++
            playStates[player]?.copy(tick = currentTick)?.let { playStates[player] = it }
        }.repeat(Duration.ofMillis((1000.0 / song.tempo).toLong())).schedule()

        playStates[player] = SongState(song, task, tick = startAtTick)
    }

    fun getState(player: Player) = playStates[player]

    suspend fun stop(player: Player) {
        val queue = Messaging.outgoing.getSongInfo(player)
        Messaging.outgoing.setSongInfo(player, queue.copy {
            val newSongsList = songs.drop(1)
            this.songs.clear()
            this.songs.addAll(newSongsList)
            this.startingTick = 0
            this.startedPlayingAt = currentTimestamp()
            this.isPlaying = false
        })
    }

    suspend fun restartCurrentSong(player: Player) {
        val queue = Messaging.outgoing.getSongInfo(player)
        Messaging.outgoing.setSongInfo(player, queue.copy {
            this.startingTick = 0
            this.startedPlayingAt = currentTimestamp()
            this.isPlaying = true
        })
    }

    private val loader = NBSSongLoader()
    private val emptyGame = object : Game("", "") {
        // Used as a placeholder when registering the GuiModule under this Bootstrap's event node
        override fun initialize() {}
    }

    override fun hook(eventNode: EventNode<Event>) {
        guiModule.eventNode = EventNode.all("jukebox-internal-gui-module")
        eventNode.addChild(guiModule.eventNode)
        guiModule.initialize(emptyGame, guiModule.eventNode)

        val time = measureTime {
            val songMap = mutableMapOf<String, Song>()
            val songsFolder = Paths.get("songs")
            if (!songsFolder.exists()) {
                logger.warn("Songs folder doesn't exist; skipping Jukebox features.")
                return
            }
            for (child in File("songs").listFiles()) {
                val path = child.toPath()
                logger.debug("Loading song {}", path)
                try {
                    val song = loader.load(child.absolutePath, child.readBytes())
                    songMap.put(song.songName, song)
                } catch (e: Exception) {
                    Exception("Failed to load song at path $child", e).printStackTrace()
                }
            }
            songs = songMap
        }

        logger.info("Loaded ${songs.size} note block songs in ${time}.")

        val musicDiscs = MinecraftServer.getJukeboxSongRegistry().values().map(::getDiscMaterial)

        songSelectMenu = guiModule.createMenu(
            Component.translatable("jukebox.song_select.title"),
            getInventoryType(),
            isPerPlayer = false,
            allowSpectatorClicks = true
        ) {
            var i = 0
            for ((name, song) in songs.entries) {
                slot(i++, musicDiscs[i % musicDiscs.size]!!, { player ->
                    val length = song.durationInTicks / song.tempo
                    val minutes = (length / 60).toInt()
                    val seconds = (length % 60).toInt().toString().padStart(2, '0')
                    set(
                        DataComponents.ITEM_NAME,
                        Component.text(song.songName, NamedTextColor.YELLOW)
                            .append(Component.text(" ($minutes:$seconds)", NamedTextColor.DARK_GRAY))
                    )
                    set(
                        DataComponents.LORE,
                        listOf(
                            Component.text("By ", NamedTextColor.BLUE)
                                .append(
                                    Component.text(
                                        song.originalAuthor.ifEmpty { song.author },
                                        NamedTextColor.BLUE
                                    )
                                ),
                            Component.empty(),
                            Component.text("Left click to play now", NamedTextColor.YELLOW),
                            Component.text("Right click to add to queue", NamedTextColor.GOLD)
                        )
                    )
                    set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay(false, setOf(DataComponents.JUKEBOX_PLAYABLE)))
                }) {
                    isCancelled = true
                    Messaging.IO.launch {
                        val queue = Messaging.outgoing.getSongInfo(player)
                        val song = playerSongInfo {
                            songName = name
                            songLengthTicks = song.durationInTicks
                            tempo = song.tempo
                        }
                        val newQueue = playerSongQueue {
                            isPlaying = true
                            if (click is Click.Right) {
                                // Add the song to the end of the queue
                                songs.addAll(queue.songsList)
                                songs.add(song)
                                startingTick = getState(player)?.tick ?: 0
                                startedPlayingAt = currentTimestamp()
                            } else {
                                // Replace the first item in the queue
                                songs.add(song)
                                if (queue.songsList.size > 0) {
                                    songs.addAll(queue.songsList.subList(1, queue.songsList.size))
                                }
                                startedPlayingAt = currentTimestamp()
                                startingTick = 0
                            }
                        }
                        Messaging.outgoing.setSongInfo(player, newQueue)
                    }
                    menu.close(player)
                }
            }
        }

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            val job = Messaging.IO.async {
                Messaging.outgoing.getSongInfo(event.player)
            }
            // When the player loads in, start playing the song that they were listening to previously
            event.player.eventNode().addListener(EventListener.builder(PlayerLoadedEvent::class.java).handler {
                Messaging.IO.launch {
                    updateSongQueueFromIncomingMessage(event.player, job.await())
                }
            }.expireCount(1).build())
        }
    }

    private fun getDiscMaterial(song: JukeboxSong): Material? =
        Material.fromKey("minecraft:music_disc_" + song.soundEvent().key().value().substringAfter("music_disc."))

    internal fun Timestamp.toMillis() = (seconds * 1_000L) + (nanos / 1_000_000L)

    internal fun currentTimestamp(): Timestamp {
        val ms = System.currentTimeMillis()
        val seconds = ms / 1000
        val nanos = (ms % 1_000).toInt() * 1_000_000
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build()
    }

    private fun msToTicks(ms: Long): Int {
        return ms.toInt() / (1000 / ServerFlag.SERVER_TICKS_PER_SECOND)
    }

    fun updateSongQueueFromIncomingMessage(player: Player, queue: JukeboxOuterClass.PlayerSongQueue) {
        val firstItem = queue.songsList.firstOrNull()
        val current = getState(player)

        // Cancel and restart the song task with the new first song in the queue
        playStates.remove(player)?.task?.cancel()
        if (firstItem != null && queue.isPlaying) {
            play(
                firstItem.songName, player,
                (queue.startingTick + msToTicks(System.currentTimeMillis() - queue.startedPlayingAt.toMillis())).toInt()
            )
        }
    }
}
