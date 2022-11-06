package com.bluedragonmc.server

abstract class Environment {

    companion object {

        lateinit var current: Environment

        fun setEnvironment(env: Environment) {
            if (!::current.isInitialized) {
                current = env
            } else throw IllegalStateException("Tried to override current Environment")
        }
    }

    abstract val queue: com.bluedragonmc.server.api.Queue
    abstract val messagingDisabled: Boolean
    abstract val mongoHostname: String
    abstract val puffinHostname: String
    abstract val gameClasses: Collection<String>
    abstract val versionInfo: VersionInfo
    open val dbName: String = "bluedragon"
    abstract suspend fun getServerName(): String
}