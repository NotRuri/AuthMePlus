package one.ruri.authmeplus.protocol

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.google.gson.JsonParser
import one.ruri.authmeplus.AccountType
import one.ruri.authmeplus.Logger
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class SkinData(
    val value: String,
    val signature: String,
)

data class VerificationResult(
    val uuid: UUID,
)

internal class Session(
    private val plugin: JavaPlugin,
    private val log: Logger,
    crackedPlayers: Set<String> = emptySet(),
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build(),
    private val protocolManager: ProtocolManager = ProtocolLibrary.getProtocolManager(),
    private val support: Handshake = Handshake(plugin, protocolManager, log),
) {
    private var crackedPlayers: Set<String> = crackedPlayers.map { it.lowercase() }.toSet()
    private val verifiedPlayers = ConcurrentHashMap.newKeySet<String>()
    private val verifiedUUIDs = ConcurrentHashMap<String, UUID>()
    private val pendingSessions = ConcurrentHashMap<InetSocketAddress, PendingSession>()
    private val verifiedSkins = ConcurrentHashMap<String, SkinData>()
    private val secureRandom = SecureRandom()
    private val keyPair: KeyPair

    data class PendingSession(
        val username: String,
        val verifyToken: ByteArray,
        val playerRef: Player?,
    )

    init {
        log.info("Generating RSA key pair for encryption handshake...")
        keyPair = generateKeyPair()
        log.info("RSA key pair generated (1024-bit)")
    }

    fun isVerified(name: String): Boolean = name.lowercase() in verifiedPlayers

    fun getVerifiedUUID(name: String): UUID? = verifiedUUIDs[name.lowercase()]

    fun getSkinData(name: String): SkinData? = verifiedSkins[name.lowercase()]

    fun register() {
        log.info("Registering listeners...")

        registerLoginListener(PacketType.Login.Client.START) { event ->
            handleLoginStart(event)
        }

        registerLoginListener(PacketType.Login.Client.ENCRYPTION_BEGIN) { event ->
            handleEncryptionResponse(event)
        }

        log.info("Listeners registered")
    }

    fun updateCrackedPlayers(players: Set<String>) {
        crackedPlayers = players.map { it.lowercase() }.toSet()
        log.debug("Updated cracked players list: ${crackedPlayers.size} entries")
    }

    private fun clearVerifiedState(name: String) {
        val nlc = name.lowercase()
        verifiedPlayers.remove(nlc)
        verifiedUUIDs.remove(nlc)
        verifiedSkins.remove(nlc)
    }

    fun unregister() {
        protocolManager.removePacketListeners(plugin)
        verifiedPlayers.clear()
        verifiedUUIDs.clear()
        verifiedSkins.clear()
        pendingSessions.clear()
        log.info("Listeners unregistered")
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
        val player = event.player
        val address = player?.address

        if (player == null || address == null) {
            log.warning("No player or address for $username - can't intercept")
            return
        }

        log.debug("Login start for $username (${address.address?.hostAddress ?: "unknown"})")

        if (username.lowercase() in crackedPlayers) {
            log.debug("$username is in cracked_players - letting pass through")
            clearVerifiedState(username)
            return
        }

        val status = Utils.checkAccount(log, username)
        log.debug("Mojang API name check for $username: $status")

        if (status != AccountType.PREMIUM) {
            log.debug("$username not premium ($status) - letting pass through")
            clearVerifiedState(username)
            return
        }

        val verifyToken = ByteArray(4).also(secureRandom::nextBytes)
        log.debug("$username is premium - canceling START, initiating encryption")
        event.isCancelled = true
        pendingSessions[address] = PendingSession(username, verifyToken, player)

        try {
            support.sendEncryptionBegin(player, keyPair, verifyToken)
        } catch (e: Exception) {
            log.warning("Failed to send encryption begin to $username (${e.message}) - uncancelling START")
            pendingSessions.remove(address)
            event.isCancelled = false
        }
    }

    private fun handleEncryptionResponse(event: PacketEvent) {
        val player = event.player
        val address = player?.address

        if (address == null) {
            log.warning("Encryption response from unknown address - ignoring")
            return
        }

        val session = pendingSessions[address]
        if (session == null) {
            log.warning("Encryption response from ${address.address?.hostAddress ?: "unknown"} but no pending session")
            return
        }

        log.debug("Encryption response received for ${session.username}")
        event.isCancelled = true

        val sessionPlayer = session.playerRef
        if (sessionPlayer == null) {
            log.warning("No player ref in session for ${session.username}")
            pendingSessions.remove(address)
            return
        }

        if (!sessionPlayer.isOnline) {
            log.warning("Player ${session.username} went offline during handshake")
            pendingSessions.remove(address)
            return
        }

        val sharedSecret = decryptSharedSecret(event, session, address, sessionPlayer) ?: return

        verifySession(session, sharedSecret, sessionPlayer)
            .thenAccept { result ->
                Bukkit.getGlobalRegionScheduler().run(plugin) {
                    try {
                        log.debug("Enabling encryption on connection for ${session.username}...")
                        if (!support.enableEncryption(sharedSecret, sessionPlayer)) {
                            log.warning("Failed to enable encryption for ${session.username}")
                            support.disconnectClient(sessionPlayer, "Encryption setup failed")
                            return@run
                        }

                        log.debug("Encryption enabled for ${session.username}")

                        if (result == null) {
                            log.warning("Session verification failed for ${session.username} - disconnecting")
                            support.disconnectClient(sessionPlayer, "Premium verification failed. Please try again.")
                            return@run
                        }

                        support.injectFakeStart(sessionPlayer, result.uuid, session.username)
                        verifiedPlayers += session.username.lowercase()
                        log.info("Premium session fully verified: ${session.username} (${result.uuid})")
                    } catch (e: Exception) {
                        log.warning("Failed to finalize premium login: ${e.message}")
                        support.disconnectClient(sessionPlayer, "Login finalization failed")
                    } finally {
                        pendingSessions.remove(address)
                    }
                }
            }.exceptionally { e ->
                log.warning("Session verification chain failed unexpectedly: ${e.message}")
                null
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
                log.warning("RSA decrypt failed for ${session.username}: ${e.message}")
                support.disconnectClient(player, "Decryption error")
                pendingSessions.remove(address)
                return null
            }

        val verifyTokenEncrypted = event.packet.byteArrays.read(1)
        val receivedToken =
            try {
                cipher.doFinal(verifyTokenEncrypted)
            } catch (e: Exception) {
                log.warning("RSA decrypt verify token failed: ${e.message}")
                support.disconnectClient(player, "Decryption error")
                pendingSessions.remove(address)
                return null
            }

        if (!session.verifyToken.contentEquals(receivedToken)) {
            log.warning("Verify token mismatch for ${session.username}")
            support.disconnectClient(player, "Invalid verify token")
            pendingSessions.remove(address)
            return null
        }

        log.debug("Shared secret decrypted for ${session.username}")
        return sharedSecret
    }

    internal fun verifySession(
        session: PendingSession,
        sharedSecret: SecretKey,
        player: Player,
    ): CompletableFuture<VerificationResult?> {
        log.debug("Verify token matched for ${session.username} - calling hasJoined")

        val serverHash = support.computeServerHash("", sharedSecret, keyPair.public)
        val request =
            HttpRequest
                .newBuilder()
                .uri(
                    java.net.URI.create(
                        "https://sessionserver.mojang.com/session/minecraft/hasJoined" +
                            "?username=${session.username}&serverId=$serverHash",
                    ),
                ).timeout(Duration.ofSeconds(10))
                .GET()
                .build()

        log.debug("Querying Mojang session server for ${session.username}...")

        return httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                val httpCode = response.statusCode()
                log.debug("hasJoined response for ${session.username}: HTTP $httpCode")

                if (httpCode != 200 || response.body().isBlank()) {
                    log.warning("Session verification FAILED for ${session.username} (HTTP $httpCode)")
                    return@thenApply null
                }

                val uuidStr = support.extractUuidFromJson(response.body())
                if (uuidStr == null) {
                    log.warning("Could not parse hasJoined response for ${session.username}: ${response.body()}")
                    return@thenApply null
                }

                val realUuid = UUID.fromString(uuidStr)
                log.info("Session VERIFIED for ${session.username} - Mojang UUID: $realUuid")
                verifiedUUIDs[session.username.lowercase()] = realUuid

                val body = response.body()
                try {
                    val root = JsonParser.parseString(body).asJsonObject
                    val properties = root.getAsJsonArray("properties")
                    if (properties == null) {
                        log.warning("hasJoined response for ${session.username} has no properties array (no skin data available)")
                    } else {
                        for (element in properties) {
                            val prop = element.asJsonObject
                            val propName = prop.get("name").asString
                            if (propName == "textures") {
                                verifiedSkins[session.username.lowercase()] =
                                    SkinData(
                                        prop.get("value").asString,
                                        prop.get("signature").asString,
                                    )
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.warning("Failed to parse skin data from hasJoined response: ${e.message}")
                }

                VerificationResult(realUuid)
            }.exceptionally { e ->
                log.warning("Session verification request failed for ${session.username}: ${e.message}")
                null
            }
    }
}
