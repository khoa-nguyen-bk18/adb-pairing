package com.devindie.zola.adb.pairing.api

/**
 * Request describing an ADB pairing attempt.
 */
sealed interface AdbPairingRequest {
    val config: AdbPairingConfig

    /**
     * Request for pairing via QR code exchange.
     *
     * @property serviceName The mDNS service name to filter for (e.g. "studio-XXXXXXXXXX").
     * @property password The pairing password encoded in the QR code.
     * @property config Optional pairing configuration.
     */
    data class Qr(
        val serviceName: String,
        val password: String,
        override val config: AdbPairingConfig = AdbPairingConfig.DEFAULT
    ) : AdbPairingRequest

    /**
     * Request for pairing via 6-digit manual pairing code.
     *
     * @property pairingCode The 6-digit pairing code shown by Android Wireless Debugging.
     * @property port The pairing port if already discovered (e.g. via [AdbPairingManager.discoverPairingPort]).
     *                If null, mDNS pairing service discovery will be performed first.
     * @property config Optional pairing configuration.
     */
    data class Manual(
        val pairingCode: String,
        val port: Int? = null,
        override val config: AdbPairingConfig = AdbPairingConfig.DEFAULT
    ) : AdbPairingRequest
}
