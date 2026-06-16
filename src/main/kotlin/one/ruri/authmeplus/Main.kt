package one.ruri.authmeplus

import one.ruri.authmeplus.command.Dispatch
import one.ruri.authmeplus.event.Command
import one.ruri.authmeplus.event.PlayerJoin
import one.ruri.authmeplus.event.TabComplete
import one.ruri.authmeplus.protocol.Protocol
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    private var protocolHandler: Protocol? = null
    private lateinit var log: Logger

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfig()

        log = Logger(logger)
        log.debug = config.getBoolean("settings.debug", false)

        protocolHandler = Protocol(this, log).also { it.register() }
        log.info("ProtocolLib: v${Protocol.protocolLibVersion()}")

        val dispatch = Dispatch(this, config, log)
        val command = Command(dispatch)
        val tabComplete = TabComplete()
        val playerJoin = PlayerJoin(this, config, protocolHandler!!, log)

        server.pluginManager.registerEvents(playerJoin, this)
        getCommand("amp")?.let {
            it.setExecutor(command)
            it.setTabCompleter(tabComplete)
        }

        val cfg = config
        log.info(
            "AuthMePlus enabled (accept_cracked=${cfg.getBoolean(
                "settings.accept_cracked",
                false,
            )}, enabled=${cfg.getBoolean("settings.enabled", true)})",
        )
    }

    override fun onDisable() {
        protocolHandler?.unregister()
        if (::log.isInitialized) log.info("AuthMePlus disabled")
    }
}
