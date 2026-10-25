package com.devindie.zola.adb.pairing.api

import java.security.SecureRandom
import java.util.UUID

/**
 * Encapsulates the credentials and standard AOSP QR content for an ADB wireless pairing session.
 *
 * @property sessionId Unique identifier for tracking this session.
 * @property serviceName The mDNS service name (e.g. "studio-" followed by 10 alphanumeric characters).
 * @property password The random password used during SPAKE2 pairing handshake (20 alphanumeric characters).
 */
data class QrPairingSession(
    val sessionId: String,
    val serviceName: String,
    val password: String
) {
    /**
     * Returns the standard AOSP QR string formatted for Wireless Debugging:
     * `WIFI:T:ADB;S:<serviceName>;P:<password>;;`
     */
    fun qrContent(): String = "WIFI:T:ADB;S:$serviceName;P:$password;;"

    companion object {
        private val CHARS = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        private val rng = SecureRandom()

        fun randomString(length: Int): String =
            (1..length).map { CHARS[rng.nextInt(CHARS.size)] }.joinToString("")

        fun generate(): QrPairingSession = QrPairingSession(
            sessionId = UUID.randomUUID().toString(),
            serviceName = "studio-" + randomString(10),
            password = randomString(20)
        )
    }
}
