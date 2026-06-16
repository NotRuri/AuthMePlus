package one.ruri.authmeplus.protocol

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.injector.netty.channel.NettyChannelInjector
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory
import com.comphenix.protocol.reflect.accessors.Accessors
import com.comphenix.protocol.wrappers.WrappedChatComponent
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.math.BigInteger
import java.security.KeyPair
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey

internal class Handshake(
    private val plugin: JavaPlugin,
    private val protocolManager: ProtocolManager,
) {
    private companion object {
        const val ENCRYPTION_CLASS_NAME = "net.minecraft.util.Crypt"
        const val ENCRYPTION_METHOD_NAME = "setEncryptionKey"
        const val CIPHER_METHOD_NAME = "getCipher"
    }

    private val uuidRegex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()

    fun sendEncryptionBegin(
        player: Player,
        keyPair: KeyPair,
        verifyToken: ByteArray,
    ) {
        try {
            val packet = PacketContainer(PacketType.Login.Server.ENCRYPTION_BEGIN)

            if (packet.strings.getFields().isNotEmpty()) {
                packet.strings.write(0, "")
            }

            val keyModifier = packet.getSpecificModifier(java.security.PublicKey::class.java)
            var byteArrayIndex = 0
            if (keyModifier.getFields().isNotEmpty()) {
                keyModifier.write(0, keyPair.public)
            } else {
                packet.byteArrays.write(0, keyPair.public.encoded)
                byteArrayIndex = 1
            }

            packet.byteArrays.write(byteArrayIndex, verifyToken)

            if (packet.getBooleans().getFields().isNotEmpty()) {
                packet.getBooleans().writeSafely(0, true)
            }

            protocolManager.sendServerPacket(player, packet)
            plugin.logger.fine("Sent ENCRYPTION_BEGIN to ${player.name}")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send encryption begin: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    fun injectFakeStart(
        player: Player,
        uuid: java.util.UUID,
        username: String,
    ) {
        val packet = PacketContainer(PacketType.Login.Client.START)
        packet.strings.write(0, username)
        packet.getUUIDs().write(0, uuid)
        protocolManager.receiveClientPacket(player, packet, false)
        plugin.logger.fine("Injected fake START for $username with UUID $uuid")
    }

    fun disconnectClient(
        player: Player?,
        reason: String,
    ) {
        if (player == null) return

        try {
            val packet = PacketContainer(PacketType.Login.Server.DISCONNECT)
            packet.chatComponents.write(0, WrappedChatComponent.fromText(reason))
            protocolManager.sendServerPacket(player, packet)
            plugin.logger.info("Disconnected: $reason")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to disconnect: ${e::class.simpleName}: ${e.message}")
        }
    }

    fun enableEncryption(
        secretKey: SecretKey,
        player: Player,
    ): Boolean {
        return try {
            val injector =
                Accessors
                    .getMethodAccessorOrNull(
                        TemporaryPlayerFactory::class.java,
                        "getInjectorFromPlayer",
                        Player::class.java,
                    )?.invoke(null, player)

            if (injector == null) {
                plugin.logger.warning("Could not get injector for ${player.name}")
                return false
            }

            val networkManager = resolveNetworkManager(injector)
            if (networkManager == null) {
                plugin.logger.warning("Could not get NetworkManager from injector")
                return false
            }

            plugin.logger.fine("Got NetworkManager: ${networkManager::class.java.name}")

            if (trySecretKeyMethods(networkManager, secretKey)) {
                return true
            }

            if (tryCipherMethods(networkManager, secretKey)) {
                return true
            }

            plugin.logger.warning(
                "No encryption method found on ${networkManager::class.java.name} (tried SecretKey and Cipher,Cipher variants)",
            )
            false
        } catch (e: Exception) {
            plugin.logger.warning("Failed to enable encryption: ${e::class.simpleName}: ${e.message}")
            false
        }
    }

    fun computeServerHash(
        serverId: String,
        sharedSecret: SecretKey,
        publicKey: java.security.PublicKey,
    ): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(serverId.toByteArray(Charsets.US_ASCII))
        digest.update(sharedSecret.encoded)
        digest.update(publicKey.encoded)
        return BigInteger(digest.digest()).toString(16)
    }

    fun extractUuidFromJson(json: String): String? {
        val idMatch = uuidRegex.find(json) ?: return null
        val raw = idMatch.groupValues[1]

        return if (raw.length == 32) {
            "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${
                raw.substring(
                    16,
                    20,
                )
            }-${raw.substring(20)}"
        } else {
            raw
        }
    }

    private fun resolveNetworkManager(injector: Any): Any? {
        val accessor =
            Accessors.getFieldAccessorOrNull(
                NettyChannelInjector::class.java,
                "networkManager",
                Any::class.java,
            )

        return accessor?.get(injector)
    }

    private fun trySecretKeyMethods(
        networkManager: Any,
        secretKey: SecretKey,
    ): Boolean =
        try {
            val method = networkManager::class.java.getMethod(ENCRYPTION_METHOD_NAME, SecretKey::class.java)
            method.invoke(networkManager, secretKey)
            plugin.logger.fine("Encryption enabled via $ENCRYPTION_METHOD_NAME(SecretKey)")
            true
        } catch (_: NoSuchMethodException) {
            false
        }

    private fun tryCipherMethods(
        networkManager: Any,
        secretKey: SecretKey,
    ): Boolean {
        val ciphers =
            try {
                encryptDecrypt(secretKey)
            } catch (_: ClassNotFoundException) {
                plugin.logger.warning("Cannot find Crypt class - Cipher approach unavailable")
                return false
            }

        return try {
            val method =
                networkManager::class.java.getMethod(
                    ENCRYPTION_METHOD_NAME,
                    ciphers.first::class.java,
                    ciphers.second::class.java,
                )
            method.invoke(networkManager, ciphers.first, ciphers.second)
            plugin.logger.fine("Encryption enabled via $ENCRYPTION_METHOD_NAME(Cipher, Cipher)")
            true
        } catch (_: NoSuchMethodException) {
            false
        }
    }

    private fun encryptDecrypt(secretKey: SecretKey): Pair<Any, Any> {
        val encryptionClass = Class.forName(ENCRYPTION_CLASS_NAME)

        val cipherHelper =
            encryptionClass.getMethod(CIPHER_METHOD_NAME, Int::class.javaPrimitiveType!!, java.security.Key::class.java)

        val decryptCipher = cipherHelper.invoke(null, Cipher.DECRYPT_MODE, secretKey)
        val encryptCipher = cipherHelper.invoke(null, Cipher.ENCRYPT_MODE, secretKey)
        return Pair(decryptCipher, encryptCipher)
    }
}
