package one.ruri.authmeplus.event

import com.destroystokyo.paper.profile.ProfileProperty
import one.ruri.authmeplus.Logger
import one.ruri.authmeplus.protocol.Protocol
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import java.net.InetSocketAddress

class PreLogin(
    private val protocolLib: Protocol,
    private val log: Logger,
) : Listener {
    @EventHandler
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        val addr = InetSocketAddress(event.address, 0)
        val uuid = protocolLib.getVerifiedUUID(addr) ?: return
        val name = event.name

        log.debug("Overriding profile for $name with real UUID: $uuid")

        val profile = Bukkit.createProfile(uuid, name)

        val skinData = protocolLib.getSkinData(addr)
        if (skinData != null) {
            profile.setProperty(ProfileProperty("textures", skinData.value, skinData.signature))
        }

        event.playerProfile = profile
    }
}
