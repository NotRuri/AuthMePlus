package one.ruri.authmeplus.protocol

import com.comphenix.protocol.ProtocolLibrary
import one.ruri.authmeplus.Logger
import org.bukkit.plugin.java.JavaPlugin
import java.net.InetSocketAddress

class Protocol(
    plugin: JavaPlugin,
    log: Logger,
    crackedPlayers: Set<String> = emptySet(),
) {
    private val sessionHandler = Session(plugin, log, crackedPlayers)

    fun isVerified(address: InetSocketAddress?): Boolean = sessionHandler.isVerified(address)

    fun getVerifiedUUID(address: InetSocketAddress?): java.util.UUID? = sessionHandler.getVerifiedUUID(address)

    fun getSkinData(address: InetSocketAddress?): SkinData? = sessionHandler.getSkinData(address)

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
