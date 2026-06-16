package one.ruri.authmeplus

import one.ruri.authmeplus.protocol.Protocol
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    private var eventHandlers: EventHandlers? = null
    private var protocolHandler: Protocol? = null

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfig()

        protocolHandler = Protocol(this).also { it.register() }
        logger.info("ProtocolLib v${Protocol.protocolLibVersion()} - real session verification enabled")

        eventHandlers = EventHandlers(this, config, protocolHandler!!)
        eventHandlers!!.register()

        val cfg = config
        logger.info(
            "AuthMePlus enabled (accept_cracked=${cfg.getBoolean(
                "settings.accept_cracked",
                false,
            )}, enabled=${cfg.getBoolean("settings.enableplugin", true)})",
        )
    }

    override fun onDisable() {
        protocolHandler?.unregister()
        eventHandlers?.shutdown()
        logger.info("AuthMePlus disabled")
    }
}
