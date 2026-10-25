package com.devindie.zola.adb.pairing.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AdbPairingApiTest {

    @Test
    fun testGenerateSession_createsValidCredentials() {
        val session = QrPairingSession.generate()

        assertNotNull(session.sessionId)
        UUID.fromString(session.sessionId)

        assertTrue(session.serviceName.startsWith("studio-"))
        assertEquals(17, session.serviceName.length)

        assertEquals(20, session.password.length)
        assertTrue(session.password.all { it.isLetterOrDigit() })
    }

    @Test
    fun testQrContent_formatsCorrectAospString() {
        val session = QrPairingSession(
            sessionId = "test-session-id",
            serviceName = "studio-1234567890",
            password = "testPassword12345678"
        )

        val qrContent = session.qrContent()
        assertEquals(
            "WIFI:T:ADB;S:studio-1234567890;P:testPassword12345678;;",
            qrContent
        )
    }

    @Test
    fun testDefaultConfigValues() {
        val config = AdbPairingConfig.DEFAULT
        assertEquals("127.0.0.1", config.host)
        assertEquals(60_000L, config.discoveryTimeoutMs)
        assertEquals(30_000L, config.pairingTimeoutMs)
        assertEquals(30_000L, config.connectDiscoveryTimeoutMs)
        assertEquals("zola", config.keyAlias)
    }

    @Test
    fun testPairingRequestsDefaultConfig() {
        val qrReq = AdbPairingRequest.Qr("studio-test", "pass123")
        assertEquals(AdbPairingConfig.DEFAULT, qrReq.config)

        val manualReq = AdbPairingRequest.Manual("123456", 5555)
        assertEquals(AdbPairingConfig.DEFAULT, manualReq.config)
        assertEquals(5555, manualReq.port)
        assertEquals("123456", manualReq.pairingCode)
    }
}
