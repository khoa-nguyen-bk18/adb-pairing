package io.github.khoanguyenbk18.adbpairing.api

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
}
