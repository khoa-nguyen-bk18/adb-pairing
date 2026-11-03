package io.github.khoanguyenbk18.adbpairing.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AdbPairingApiTest {

    @Test
    fun testManualRequest_validInput() {
        val request = AdbPairingRequest.Manual(pairingCode = "123456", port = 5555)
        assertEquals("123456", request.pairingCode)
        assertEquals(5555, request.port)
        assertTrue(request.toString().contains("******"))
        assertFalse(request.toString().contains("123456"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManualRequest_invalidLength_throws() {
        AdbPairingRequest.Manual(pairingCode = "12345")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManualRequest_nonDigits_throws() {
        AdbPairingRequest.Manual(pairingCode = "12345a")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManualRequest_invalidPort_throws() {
        AdbPairingRequest.Manual(pairingCode = "123456", port = 70000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManualRequest_zeroPort_throws() {
        AdbPairingRequest.Manual(pairingCode = "123456", port = 0)
    }

    @Test
    fun testQrRequest_validInput() {
        val request = AdbPairingRequest.Qr(serviceName = "studio-test", password = "secretPassword")
        assertEquals("studio-test", request.serviceName)
        assertEquals("secretPassword", request.password)
        assertTrue(request.toString().contains("******"))
        assertFalse(request.toString().contains("secretPassword"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testQrRequest_blankServiceName_throws() {
        AdbPairingRequest.Qr(serviceName = "  ", password = "pass")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testQrRequest_blankPassword_throws() {
        AdbPairingRequest.Qr(serviceName = "studio-test", password = "   ")
    }

    @Test
    fun testQrPairingSession_generateAndRedaction() {
        val session = QrPairingSession.generate()
        assertNotNull(session.sessionId)
        UUID.fromString(session.sessionId)
        assertTrue(session.serviceName.startsWith("studio-"))
        assertEquals(17, session.serviceName.length)
        assertEquals(20, session.password.length)
        assertTrue(session.toString().contains("******"))
        assertFalse(session.toString().contains(session.password))
        assertTrue(session.qrContent().contains(session.password))
    }

    @Test
    fun testAdbIdentity_exposesKeyAndCertificate() {
        val dummyPrivKey = object : java.security.PrivateKey {
            override fun getAlgorithm(): String = "RSA"
            override fun getFormat(): String = "PKCS#8"
            override fun getEncoded(): ByteArray = byteArrayOf(1, 2, 3)
        }
        val dummyCert = object : java.security.cert.Certificate("X.509") {
            override fun getEncoded(): ByteArray = byteArrayOf(4, 5, 6)
            override fun verify(key: java.security.PublicKey?) {}
            override fun verify(key: java.security.PublicKey?, sigProvider: String?) {}
            override fun toString(): String = "DummyCert"
            override fun getPublicKey(): java.security.PublicKey? = null
        }
        val identity = object : AdbIdentity {
            override val name: String = "test"
            override val privateKey: java.security.PrivateKey = dummyPrivKey
            override val certificate: java.security.cert.Certificate = dummyCert
            override fun encodedPublicKey(): ByteArray = byteArrayOf(7, 8, 9)
            override fun sign(token: ByteArray): ByteArray = byteArrayOf(10)
            override fun createSslContext(): javax.net.ssl.SSLContext = javax.net.ssl.SSLContext.getDefault()
        }
        assertEquals(dummyPrivKey, identity.privateKey)
        assertEquals(dummyCert, identity.certificate)
    }

    @Test
    fun testAwaitPairing_successAndProgress() = runTest {
        val progressEvents = mutableListOf<AdbPairingEvent>()
        val expectedEndpoint = AdbConnectionEndpoint("127.0.0.1", 41234)
        val manager = object : AdbPairingManager {
            override suspend fun getIdentity(): AdbIdentity = error("unused")
            override fun getIdentitySync(): AdbIdentity = error("unused")
            override fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent> = flowOf(
                AdbPairingEvent.SearchingForPairingService,
                AdbPairingEvent.PairingInProgress,
                AdbPairingEvent.Success(expectedEndpoint)
            )
            override fun discoverPairingEndpoints(options: AdbDiscoveryOptions): Flow<AdbDiscoveryEvent> = emptyFlow()
            override fun discoverConnectionEndpoints(): Flow<AdbConnectionEndpoint> = emptyFlow()
        }

        val result = manager.awaitPairing("123456") { progressEvents.add(it) }
        assertTrue(result.isSuccess)
        assertEquals(expectedEndpoint, result.getOrNull())
        assertEquals(2, progressEvents.size)
    }

    @Test
    fun testAwaitPairing_failureWrapsAdbPairingException() = runTest {
        val manager = object : AdbPairingManager {
            override suspend fun getIdentity(): AdbIdentity = error("unused")
            override fun getIdentitySync(): AdbIdentity = error("unused")
            override fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent> = flowOf(
                AdbPairingEvent.Failed(AdbPairingFailure.InvalidPairingCode)
            )
            override fun discoverPairingEndpoints(options: AdbDiscoveryOptions): Flow<AdbDiscoveryEvent> = emptyFlow()
            override fun discoverConnectionEndpoints(): Flow<AdbConnectionEndpoint> = emptyFlow()
        }

        val result = manager.awaitPairing(QrPairingSession.generate())
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as AdbPairingException
        assertEquals(AdbPairingFailure.InvalidPairingCode, ex.failure)
    }
}
