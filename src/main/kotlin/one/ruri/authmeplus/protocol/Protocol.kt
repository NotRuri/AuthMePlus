package one.ruri.authmeplus.protocol

import com.comphenix.protocol.ProtocolLibrary
import one.ruri.authmeplus.Logger
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class Protocol(
    plugin: JavaPlugin,
    log: Logger,
    crackedPlayers: Set<String> = emptySet(),
) {
    private val sessionHandler = Session(plugin, log, crackedPlayers)

    fun isVerified(name: String): Boolean = sessionHandler.isVerified(name)

    fun getVerifiedUUID(name: String): UUID? = sessionHandler.getVerifiedUUID(name)

    fun getSkinData(name: String): SkinData? = sessionHandler.getSkinData(name)

    fun updateCrackedPlayers(crackedPlayers: Set<String>) {
        sessionHandler.updateCrackedPlayers(crackedPlayers)
    }

    fun register() {
        sessionHandler.register()
    }

    fun unregister() {
        sessionHandler.unregister()
    }

    companion object {
        fun protocolLibVersion(): String = ProtocolLibrary.getPlugin().pluginMeta.version
    }
}
