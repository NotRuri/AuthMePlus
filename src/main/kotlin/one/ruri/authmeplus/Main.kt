package one.ruri.authmeplus

import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    private var handlers: Handlers? = null

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfig()

        handlers = Handlers(this, config)
        handlers!!.register()

        logger.info("AuthMePlus enabled")
    }

    override fun onDisable() {
        handlers?.shutdown()
        logger.info("AuthMePlus disabled")
    }
}
