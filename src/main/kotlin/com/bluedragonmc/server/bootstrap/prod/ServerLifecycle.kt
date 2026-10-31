package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.shutdown.ServerShutdownNotice
import com.bluedragonmc.server.shutdown.ShutdownCountdown
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.server.ServerTickMonitorEvent
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Exposes the server's Kubernetes lifecycle endpoints and handles graceful shutdown.
 *
 * - `/health` is used as a liveness probe. It fails when the server stops ticking or loses
 *   its messaging connection.
 * - `/ready` is used as a readiness probe. It fails while the server is draining and when
 *   the server has not ticked recently.
 * - `/drain` is called by the Kubernetes `preStop` hook. It starts a countdown, notifies
 *   players, and only completes once the server is safe to stop.
 */
object ServerLifecycle : Bootstrap(EnvType.PRODUCTION) {

    private const val NANOS_PER_MILLI = 1_000_000L

    /** Always leave at least this much of the countdown visible. */
    private const val MIN_VISIBLE_COUNTDOWN_SECONDS = 60L

    private val lifecyclePort =
        (System.getenv("BLUEDRAGON_HEALTH_PORT")?.toIntOrNull() ?: 8081).coerceIn(1, 65535)
    private val tickTimeoutNanos =
        envLong("BLUEDRAGON_TICK_TIMEOUT_MS", 100).coerceAtLeast(1) * NANOS_PER_MILLI
    private val countdownSeconds =
        envLong("BLUEDRAGON_SHUTDOWN_COUNTDOWN_SECONDS", 2 * 60 * 60L).coerceAtLeast(1)
    private val countdownHiddenSeconds =
        envLong("BLUEDRAGON_COUNTDOWN_HIDDEN_SECONDS", 60 * 60L).coerceAtLeast(0)
    private val effectiveHiddenSeconds =
        countdownHiddenSeconds.coerceIn(0L, (countdownSeconds - MIN_VISIBLE_COUNTDOWN_SECONDS).coerceAtLeast(0L))
    private val drainQuiescenceNanos =
        envLong("BLUEDRAGON_DRAIN_QUIESCENCE_MS", 15_000L) * NANOS_PER_MILLI

    private val lastTickNanos = AtomicLong(0L)

    @Volatile
    private var started = false

    @Volatile
    private var draining = false

    @Volatile
    private var shutdownComplete = false

    private val cleanShutdownStarted = AtomicBoolean(false)

    @Volatile
    private var drainFuture: CompletableFuture<Unit>? = null

    @Volatile
    private var countdownTask: ScheduledFuture<*>? = null

    private var countdown: ShutdownCountdown? = null
    private var emptySinceNanos: Long? = null

    @Volatile
    private var httpServer: HttpServer? = null

    @Volatile
    private var httpExecutor: ExecutorService? = null

    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "server-shutdown-countdown").apply { isDaemon = true }
        }

    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(ServerTickMonitorEvent::class.java) {
            lastTickNanos.set(System.nanoTime())
        }

        if (System.getenv("BLUEDRAGON_LIFECYCLE_DISABLED") != null) {
            logger.warn("Server lifecycle endpoints are disabled by BLUEDRAGON_LIFECYCLE_DISABLED.")
            return
        }

        try {
            val executor = Executors.newVirtualThreadPerTaskExecutor()
            val server = HttpServer.create(InetSocketAddress(lifecyclePort), 0)
            server.executor = executor
            server.createContext("/health") { exchange ->
                val healthy = isHealthy()
                respond(exchange, if (healthy) 200 else 503, if (healthy) "ok" else "unhealthy")
            }
            server.createContext("/ready") { exchange ->
                val ready = isReady()
                respond(exchange, if (ready) 200 else 503, if (ready) "ready" else "not ready")
            }
            server.createContext("/drain") { exchange -> handleDrain(exchange) }
            server.start()

            httpServer = server
            httpExecutor = executor
            logger.info("Server lifecycle endpoints listening on port $lifecyclePort")
            Runtime.getRuntime().addShutdownHook(Thread(::stopHttpServer, "server-lifecycle-shutdown"))
        } catch (e: Exception) {
            logger.error("Failed to start server lifecycle endpoints on port $lifecyclePort", e)
            throw e
        }
    }

    /**
     * Marks the server as fully started. This is called after the Minecraft listener is bound.
     */
    fun markReady() {
        started = true
        logger.info("Server is ready to accept connections.")
    }

    /**
     * Whether a shutdown drain has been requested.
     */
    fun isDraining(): Boolean = draining

    /**
     * Starts the shutdown countdown, or returns the already-running drain.
     */
    fun beginDrain(): CompletableFuture<Unit> = synchronized(this) {
        drainFuture?.let { return it }

        draining = true
        logger.info("Drain requested; starting a ${countdownSeconds}s shutdown countdown.")
        val future = CompletableFuture<Unit>()
        drainFuture = future

        try {
            countdownTask = scheduler.scheduleAtFixedRate(::drainTick, 0, 1, TimeUnit.SECONDS)
        } catch (e: RejectedExecutionException) {
            logger.error("Could not schedule the shutdown countdown", e)
            future.complete(Unit)
        }

        future
    }

    private fun drainTick() {
        if (shutdownComplete) return

        try {
            val players = MinecraftServer.getConnectionManager().onlinePlayers

            val activeCountdown = countdown
            if (activeCountdown == null) {
                if (players.isEmpty()) {
                    val emptySince = emptySinceNanos
                    if (emptySince == null) {
                        emptySinceNanos = System.nanoTime()
                    } else if (System.nanoTime() - emptySince >= drainQuiescenceNanos) {
                        completeDrain(kickPlayers = false)
                    }
                    return
                }

                emptySinceNanos = null
                val newCountdown = ShutdownCountdown(
                    totalSeconds = countdownSeconds,
                    onTick = ::updateNotice,
                    onMilestone = ::announceMilestone,
                    onComplete = { completeDrain(kickPlayers = true) },
                )
                countdown = newCountdown
                newCountdown.start()
                return
            }

            activeCountdown.tick()

            if (players.isEmpty()) {
                val emptySince = emptySinceNanos
                if (emptySince == null) {
                    emptySinceNanos = System.nanoTime()
                } else if (System.nanoTime() - emptySince >= drainQuiescenceNanos) {
                    completeDrain(kickPlayers = false)
                }
            } else {
                emptySinceNanos = null
            }
        } catch (e: Throwable) {
            // If we let an exception get thrown outside this method, the timer would cancel
            // itself, and the preStop hook would stay waiting forever. Instead, just log the
            // error and hope that the server can continue the shutdown process normally.
            logger.error("Error while running the shutdown countdown", e)
        }
    }

    /**
     * The countdown is hidden for the first part of the drain so players who
     * finish their game during that window are moved without a shutdown warning.
     */
    private fun shouldAnnounce(remainingSeconds: Long): Boolean =
        remainingSeconds <= countdownSeconds - effectiveHiddenSeconds

    private fun updateNotice(remainingSeconds: Long) {
        if (!shouldAnnounce(remainingSeconds)) return

        val line = if (remainingSeconds <= 0) {
            Component.translatable("server.shutdown.sidebar.now")
        } else {
            val time = ShutdownCountdown.formatSidebar(remainingSeconds)
            Component.translatable("server.shutdown.sidebar", Component.text(time))
        }

        ServerShutdownNotice.update(line.color(NamedTextColor.RED))
    }

    private fun announceMilestone(remainingSeconds: Long) {
        if (!shouldAnnounce(remainingSeconds)) return

        val message = Component.translatable(
            "server.shutdown.chat",
            Component.text(ShutdownCountdown.formatChat(remainingSeconds)),
        ).color(NamedTextColor.RED)
        sendToOnlinePlayers(message)
    }

    private fun sendToOnlinePlayers(message: Component) {
        val players = MinecraftServer.getConnectionManager().onlinePlayers
        if (players.isEmpty()) return

        runCatching {
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                players.forEach { player ->
                    runCatching { player.sendMessage(message) }
                }
            }
        }.onFailure {
            logger.warn("Failed to schedule a shutdown chat message", it)
        }
    }

    private fun completeDrain(kickPlayers: Boolean) {
        if (shutdownComplete) return
        shutdownComplete = true
        draining = true
        countdownTask?.cancel(false)
        countdownTask = null

        if (kickPlayers) {
            updateNotice(0)
            val players = MinecraftServer.getConnectionManager().onlinePlayers
            if (players.isNotEmpty()) {
                val message = Component.translatable("server.shutdown.chat.now").color(NamedTextColor.RED)
                val kickMessage = Component.translatable("server.shutdown.kick").color(NamedTextColor.RED)
                val kicked = CompletableFuture<Unit>()

                runCatching {
                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        try {
                            players.forEach { player ->
                                runCatching {
                                    player.sendMessage(message)
                                    player.kick(kickMessage)
                                }
                            }
                        } finally {
                            kicked.complete(Unit)
                        }
                    }
                }.onFailure {
                    logger.error("Failed to schedule player kicks during shutdown", it)
                    kicked.complete(Unit)
                }

                runCatching { kicked.get(5, TimeUnit.SECONDS) }
                // Give disconnect packets a moment to reach the proxy before Kubernetes
                // sends SIGTERM and the process stops.
                runCatching { Thread.sleep(2_000) }
            }
        }

        ServerShutdownNotice.clear()
        finishShutdown()
        drainFuture?.complete(Unit)
        logger.info("Shutdown drain finished (kickPlayers={}).", kickPlayers)
    }

    /**
     * Stops the Minecraft server cleanly, running Minestom's shutdown tasks before
     * the preStop hook returns and Kubernetes sends SIGTERM.
     */
    private fun finishShutdown() {
        if (!cleanShutdownStarted.compareAndSet(false, true)) return

        logger.info("Stopping the Minecraft server cleanly...")

        runCatching { Messaging.shutdownIO() }
            .onFailure { logger.warn("Failed to drain the messaging coroutine scope", it) }

        runCatching { MinecraftServer.stopCleanly() }
            .onFailure { logger.error("Failed to stop the Minecraft server cleanly", it) }

        runCatching { Messaging.closeOutgoing() }
            .onFailure { logger.warn("Failed to close the outgoing gRPC channel", it) }

        logger.info("Minecraft server stopped cleanly; waiting for SIGTERM.")
    }

    private fun isTicking(): Boolean {
        val lastTick = lastTickNanos.get()
        return lastTick != 0L && System.nanoTime() - lastTick <= tickTimeoutNanos
    }

    private fun isHealthy(): Boolean = isTicking() && Messaging.isConnected()

    private fun isReady(): Boolean = started && !draining && isTicking()

    private fun handleDrain(exchange: HttpExchange) {
        val future = beginDrain()
        try {
            future.join()
        } catch (e: Throwable) {
            logger.error("The shutdown drain did not complete normally", e)
        }
        finishShutdown()
        respond(exchange, 200, "drained")
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun stopHttpServer() {
        runCatching { httpServer?.stop(0) }
        httpServer = null
        runCatching { httpExecutor?.shutdownNow() }
        httpExecutor = null
        scheduler.shutdownNow()
    }

    private fun envLong(name: String, default: Long): Long =
        System.getenv(name)?.toLongOrNull()?.coerceAtLeast(0) ?: default
}
