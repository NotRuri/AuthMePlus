package one.ruri.authmeplus.event

import com.destroystokyo.paper.profile.ProfileProperty
import one.ruri.authmeplus.Logger
import one.ruri.authmeplus.protocol.Protocol
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent

class PreLogin(
    private val protocolLib: Protocol,
    private val log: Logger,
) : Listener {
    @EventHandler
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        val name = event.name
        val uuid = protocolLib.getVerifiedUUID(name) ?: return

        log.debug("Overriding profile for $name with real UUID: $uuid")

        val profile = Bukkit.createProfile(uuid, name)

        val skinData = protocolLib.getSkinData(name)
        if (skinData != null) {
            profile.setProperty(ProfileProperty("textures", skinData.value, skinData.signature))
        }

        event.playerProfile = profile
    }
}
