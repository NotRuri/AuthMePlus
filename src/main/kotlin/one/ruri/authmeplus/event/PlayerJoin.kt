package one.ruri.authmeplus.event

import com.destroystokyo.paper.profile.ProfileProperty
import fr.xephi.authme.api.v3.AuthMeApi
import one.ruri.authmeplus.Logger
import one.ruri.authmeplus.Utils
import one.ruri.authmeplus.protocol.Protocol
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class PlayerJoin(
    private val plugin: JavaPlugin,
    private val cfg: FileConfiguration,
    private val protocolLib: Protocol,
    private val log: Logger,
) : Listener {
    private val api = AuthMeApi.getInstance()

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val name = player.name
        val ip = player.address?.address?.hostAddress ?: "unknown"

        log.debug("Join event received: $name ($ip)")

        if (shouldSkip(player, name)) return

        if (protocolLib.isVerified(name)) {
            handleVerifiedPlayer(player, name)
            return
        }

        log.debug("Session not verified for $name - using normal AuthMe handling")
    }

    private fun shouldSkip(
        player: Player,
        name: String,
    ): Boolean {
        if (!cfg.getBoolean("settings.enabled", true)) {
            log.debug("Plugin disabled via config - skipping auth handling for $name")
            return true
        }
        if (api.isAuthenticated(player)) {
            log.debug("$name already authenticated via AuthMe - skipping")
            return true
        }
        return false
    }

    private fun handleVerifiedPlayer(
        player: Player,
        name: String,
    ) {
        log.info("Session verified for $name - auto-logging in")
        player.scheduler.run(plugin, { _ ->
            if (!player.isOnline) {
                log.warning("$name went offline before auto-login could complete")
                return@run
            }

            autoRegisterIfAbsent(player, name)
            forceLoginIfNeeded(player, name)
            restoreSkinIfEnabled(player, name)
        }, null)
    }

    private fun autoRegisterIfAbsent(
        player: Player,
        name: String,
    ) {
        if (api.isRegistered(name)) return

        val randomPass =
            java.util.UUID
                .randomUUID()
                .toString()
                .replace("-", "")
        api.registerPlayer(name, randomPass)
        log.info("Auto-registered verified player: $name")
    }

    private fun forceLoginIfNeeded(
        player: Player,
        name: String,
    ) {
        if (!api.isAuthenticated(player)) {
            api.forceLogin(player)
            player.sendMessage(
                Utils.getMessage(
                    cfg,
                    "messages.auto_login_premium",
                    "&aYour premium account has been verified!",
                ),
            )
            log.info("Auto-logged verified player: $name")
        } else {
            log.debug("$name already authenticated - no action needed")
        }
    }

    private fun restoreSkinIfEnabled(
        player: Player,
        name: String,
    ) {
        if (!cfg.getBoolean("settings.restore_skins", true)) {
            log.debug("Skin restoration disabled by config for $name")
            return
        }

        val playerIp = player.address?.address?.hostAddress
        log.debug(
            "Skin restoration check for $name: ip=$playerIp, isVerified=${protocolLib.isVerified(name)}",
        )

        val skinData = protocolLib.getSkinData(name)
        if (skinData == null) {
            log.warning(
                "No skin data found for $name (ip=$playerIp) — verifiedSkins may not have been populated during handshake",
            )
            return
        }

        log.debug(
            "Skin data found for $name: value.length=${skinData.value.length}, signature.length=${skinData.signature.length}",
        )
        try {
            val profile = player.playerProfile.clone()
            val beforeProps = profile.properties.size
            profile.setProperty(ProfileProperty("textures", skinData.value, skinData.signature))
            player.playerProfile = profile
            log.info(
                "Profile set for $name: had $beforeProps properties before, textures property added, playerProfile updated",
            )
        } catch (e: Exception) {
            log.warning("Failed to apply skin to profile for $name: ${e.message}")
        }
    }
}
