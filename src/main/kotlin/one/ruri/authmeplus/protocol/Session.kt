package one.ruri.authmeplus.protocol

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import one.ruri.authmeplus.AccountType
import one.ruri.authmeplus.Utils
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

internal class Session(
    private val plugin: JavaPlugin,
) {
    private val protocolManager = ProtocolLibrary.getProtocolManager()
    private val verifiedIps = ConcurrentHashMap.newKeySet<String>()
    private val pendingSessions = ConcurrentHashMap<InetSocketAddress, PendingSession>()
    private val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    private val secureRandom = SecureRandom()
    private val keyPair: KeyPair
    private val support = Handshake(plugin, protocolManager)

    data class PendingSession(
        val username: String,
        val verifyToken: ByteArray,
        val playerRef: Player?,
    )

    init {
        plugin.logger.info("Generating RSA key pair for encryption handshake...")
        keyPair = generateKeyPair()
        plugin.logger.info("RSA key pair generated (1024-bit)")
    }

    fun isVerified(address: InetSocketAddress?): Boolean = address?.address?.hostAddress in verifiedIps

    fun register() {
        plugin.logger.info("Registering ProtocolLib async packet listeners...")

        registerLoginListener(PacketType.Login.Client.START) { event ->
            handleLoginStart(event)
        }

        registerLoginListener(PacketType.Login.Client.ENCRYPTION_BEGIN) { event ->
            handleEncryptionResponse(event)
        }

        plugin.logger.info("ProtocolLib async listeners registered")
    }

    fun unregister() {
        protocolManager.removePacketListeners(plugin)
        verifiedIps.clear()
        pendingSessions.clear()
        plugin.logger.info("ProtocolLib listeners unregistered")
    }

    private fun registerLoginListener(
        type: PacketType,
        handler: (PacketEvent) -> Unit,
    ) {
        protocolManager.addPacketListener(
            object : PacketAdapter(
                PacketAdapter
                    .params()
                    .plugin(plugin)
                    .optionAsync()
                    .listenerPriority(ListenerPriority.LOWEST)
                    .types(type),
            ) {
                override fun onPacketReceiving(event: PacketEvent) {
                    handler(event)
                }
            },
        )
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(1024)
        return generator.generateKeyPair()
    }

    private fun handleLoginStart(event: PacketEvent) {
        val username = event.packet.strings.read(0)
        val player = event.getPlayer()
        val address = player?.address

        if (player == null || address == null) {
            plugin.logger.warning("No player or address for $username - can't intercept")
            return
        }

        plugin.logger.info("Login start for $username (${address.address?.hostAddress ?: "unknown"})")

        val status = Utils.checkAccount(plugin.logger, username)
        plugin.logger.info("Mojang API name check for $username: $status")

        if (status != AccountType.PREMIUM) {
            plugin.logger.fine("$username not premium ($status) - letting pass through")
            return
        }

        val verifyToken = ByteArray(4).also(secureRandom::nextBytes)
        plugin.logger.info("$username is premium - canceling START, initiating encryption")
        event.isCancelled = true
        pendingSessions[address] = PendingSession(username, verifyToken, player)

        try {
            support.sendEncryptionBegin(player, keyPair, verifyToken)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send encryption begin to $username (${e.message}) - uncancelling START")
            pendingSessions.remove(address)
            event.isCancelled = false
        }
    }

    private fun handleEncryptionResponse(event: PacketEvent) {
        val player = event.getPlayer()
        val address = player?.address

        if (address == null) {
            plugin.logger.warning("Encryption response from unknown address - ignoring")
            return
        }

        val session = pendingSessions[address]
        if (session == null) {
            plugin.logger.warning("Encryption response from ${address.address?.hostAddress ?: "unknown"} but no pending session")
            return
        }

        plugin.logger.info("Encryption response received for ${session.username}")
        event.isCancelled = true

        val sessionPlayer = session.playerRef
        if (sessionPlayer == null) {
            plugin.logger.warning("No player ref in session for ${session.username}")
            pendingSessions.remove(address)
            return
        }

        if (!sessionPlayer.isOnline) {
            plugin.logger.warning("Player ${session.username} went offline during handshake")
            pendingSessions.remove(address)
            return
        }

        val sharedSecret = decryptSharedSecret(event, session, address, sessionPlayer) ?: return
        val realUuid = verifySession(session, sharedSecret, address, sessionPlayer) ?: return

        Bukkit.getGlobalRegionScheduler().run(plugin) {
            try {
                plugin.logger.fine("Enabling encryption on connection for ${session.username}...")
                if (!support.enableEncryption(sharedSecret, sessionPlayer)) {
                    plugin.logger.warning("Failed to enable encryption for ${session.username}")
                    support.disconnectClient(sessionPlayer, "Encryption setup failed")
                    return@run
                }

                plugin.logger.fine("Encryption enabled for ${session.username}")
                support.injectFakeStart(sessionPlayer, realUuid, session.username)
                address.address?.hostAddress?.let(verifiedIps::add)
                plugin.logger.info("Premium session fully verified: ${session.username} ($realUuid)")
            } catch (e: Exception) {
                plugin.logger.warning("Failed to finalize premium login: ${e.message}")
                support.disconnectClient(sessionPlayer, "Login finalization failed")
            } finally {
                pendingSessions.remove(address)
            }
        }
    }

    private fun decryptSharedSecret(
        event: PacketEvent,
        session: PendingSession,
        address: InetSocketAddress,
        player: Player,
    ): SecretKey? {
        val cipher = Cipher.getInstance("RSA")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)

        val sharedSecretEncrypted = event.packet.byteArrays.read(0)
        val sharedSecret =
            try {
                SecretKeySpec(cipher.doFinal(sharedSecretEncrypted), "AES")
            } catch (e: Exception) {
                plugin.logger.warning("RSA decrypt failed for ${session.username}: ${e.message}")
                support.disconnectClient(player, "Decryption error")
                pendingSessions.remove(address)
                return null
            }

        val verifyTokenEncrypted = event.packet.byteArrays.read(1)
        val receivedToken =
            try {
                cipher.doFinal(verifyTokenEncrypted)
            } catch (e: Exception) {
                plugin.logger.warning("RSA decrypt verify token failed: ${e.message}")
                support.disconnectClient(player, "Decryption error")
                pendingSessions.remove(address)
                return null
            }

        if (!session.verifyToken.contentEquals(receivedToken)) {
            plugin.logger.warning("Verify token mismatch for ${session.username}")
            support.disconnectClient(player, "Invalid verify token")
            pendingSessions.remove(address)
            return null
        }

        plugin.logger.fine("Shared secret decrypted for ${session.username}")
        return sharedSecret
    }

    private fun verifySession(
        session: PendingSession,
        sharedSecret: SecretKey,
        address: InetSocketAddress,
        player: Player,
    ): UUID? {
        plugin.logger.info("Verify token matched for ${session.username} - calling hasJoined")

        val serverHash = support.computeServerHash("", sharedSecret, keyPair.public)
        val request =
            HttpRequest
                .newBuilder()
                .uri(
                    java.net.URI.create(
                        "https://sessionserver.mojang.com/session/minecraft/hasJoined" +
                            "?username=${session.username}&serverId=$serverHash",
                    ),
                ).GET()
                .build()

        val response =
            try {
                plugin.logger.info("Querying Mojang session server for ${session.username}...")
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (e: Exception) {
                plugin.logger.warning("Session verification request failed for ${session.username}: ${e.message}")
                support.disconnectClient(player, "Invalid session")
                pendingSessions.remove(address)
                return null
            }

        val httpCode = response.statusCode()
        plugin.logger.info("hasJoined response for ${session.username}: HTTP $httpCode")

        if (httpCode != 200 || response.body().isBlank()) {
            plugin.logger.warning("Session verification FAILED for ${session.username} (HTTP $httpCode)")
            support.disconnectClient(player, "Invalid session")
            pendingSessions.remove(address)
            return null
        }

        val uuidStr = support.extractUuidFromJson(response.body())
        if (uuidStr == null) {
            plugin.logger.warning("Could not parse hasJoined response for ${session.username}: ${response.body()}")
            support.disconnectClient(player, "Invalid session data")
            pendingSessions.remove(address)
            return null
        }

        val realUuid = UUID.fromString(uuidStr)
        plugin.logger.info("Session VERIFIED for ${session.username} - Mojang UUID: $realUuid")
        return realUuid
    }
}
