package one.ruri.authmeplus.protocol

import com.comphenix.protocol.ProtocolLibrary
import org.bukkit.plugin.java.JavaPlugin
import java.net.InetSocketAddress

class Protocol(
    plugin: JavaPlugin,
) {
    private val sessionHandler = Session(plugin)

    fun isVerified(address: InetSocketAddress?): Boolean = sessionHandler.isVerified(address)

    fun getSkinData(address: InetSocketAddress?): SkinData? = sessionHandler.getSkinData(address)

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
