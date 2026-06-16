package one.ruri.authmeplus

import org.bukkit.plugin.java.JavaPlugin

class AuthMePlus : JavaPlugin() {
    private var handlers: Handlers? = null

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfig()

        val authMe = Bridge(logger)
        if (!authMe.findAuthMeApi()) {
            logger.warning(
                "AuthMe API not found via reflection. Plugin will still run but cannot force-login players until AuthMe is present.",
            )
        }

        handlers = Handlers(this, config, authMe)
        handlers!!.register()

        logger.info("AuthMePlus enabled")
    }

    override fun onDisable() {
        handlers?.shutdown()
        logger.info("AuthMePlus disabled")
    }
}
