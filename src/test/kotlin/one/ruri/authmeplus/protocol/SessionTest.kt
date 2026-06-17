package one.ruri.authmeplus.protocol

import com.comphenix.protocol.ProtocolManager
import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.crypto.spec.SecretKeySpec

class SessionTest {
    private fun createSession(httpClient: HttpClient = mockk()): Session {
        val plugin = mockk<JavaPlugin>()
        val log =
            one.ruri.authmeplus.Logger(
                java.util.logging.Logger
                    .getLogger("test"),
            )
        val protocolManager = mockk<ProtocolManager>()
        val handshake = Handshake(plugin, protocolManager, log)
        return Session(
            plugin = plugin,
            log = log,
            httpClient = httpClient,
            protocolManager = protocolManager,
            support = handshake,
        )
    }

    private fun session(name: String = "TestUser") = Session.PendingSession(name, byteArrayOf(), null)

    private fun secretKey() = SecretKeySpec(ByteArray(16), "AES")

    @Test
    fun `returns null when HTTP request throws exception`() {
        val httpClient = mockk<HttpClient>()
        val future = CompletableFuture<HttpResponse<String>>()
        every { httpClient.sendAsync(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>()) } returns future
        future.completeExceptionally(RuntimeException("Connection refused"))

        val result = createSession(httpClient).verifySession(session(), secretKey(), mockk<Player>()).get()

        assertNull(result)
    }

    @Test
    fun `returns null on non-200 status code`() {
        val httpClient = mockk<HttpClient>()
        val response = mockk<HttpResponse<String>>()
        every { response.statusCode() } returns 404
        every { response.body() } returns "Not Found"
        val future = CompletableFuture.completedFuture(response)
        every { httpClient.sendAsync(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>()) } returns future

        val result = createSession(httpClient).verifySession(session(), secretKey(), mockk<Player>()).get()

        assertNull(result)
    }

    @Test
    fun `returns null on 200 with blank body`() {
        val httpClient = mockk<HttpClient>()
        val response = mockk<HttpResponse<String>>()
        every { response.statusCode() } returns 200
        every { response.body() } returns ""
        val future = CompletableFuture.completedFuture(response)
        every { httpClient.sendAsync(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>()) } returns future

        val result = createSession(httpClient).verifySession(session(), secretKey(), mockk<Player>()).get()

        assertNull(result)
    }

    @Test
    fun `returns null when JSON response has no uuid`() {
        val httpClient = mockk<HttpClient>()
        val response = mockk<HttpResponse<String>>()
        every { response.statusCode() } returns 200
        every { response.body() } returns """{"name":"TestUser"}"""
        val future = CompletableFuture.completedFuture(response)
        every { httpClient.sendAsync(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>()) } returns future

        val result = createSession(httpClient).verifySession(session(), secretKey(), mockk<Player>()).get()

        assertNull(result)
    }

    @Test
    fun `returns VerificationResult on successful verification`() {
        val httpClient = mockk<HttpClient>()
        val response = mockk<HttpResponse<String>>()
        every { response.statusCode() } returns 200
        every { response.body() } returns """{"id":"550e8400e29b41d4a716446655440000","name":"TestUser"}"""
        val future = CompletableFuture.completedFuture(response)
        every { httpClient.sendAsync(any<HttpRequest>(), any<HttpResponse.BodyHandler<*>>()) } returns future

        val result = createSession(httpClient).verifySession(session(), secretKey(), mockk<Player>()).get()

        assertNotNull(result)
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), result?.uuid)
    }
}
