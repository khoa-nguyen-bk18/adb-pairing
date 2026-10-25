package io.github.khoanguyenbk18.adbpairing.api

/**
 * Request describing an ADB pairing attempt.
 */
sealed interface AdbPairingRequest {
    /**
     * Request for pairing via 6-digit manual pairing code.
     *
     * @property pairingCode The 6-digit pairing code shown by Android Wireless Debugging.
     * @property port The pairing port if already known; if null, discovery will be performed.
     */
    data class Manual(
        val pairingCode: String,
        val port: Int? = null
    ) : AdbPairingRequest {
        init {
            require(pairingCode.length == 6 && pairingCode.all { it.isDigit() }) {
                "Pairing code must be exactly 6 decimal digits"
            }
            if (port != null) {
                require(port in 1..65535) { "Port must be in range 1..65535" }
            }
        }

        override fun toString(): String = "Manual(pairingCode=******, port=$port)"
    }

    /**
     * Request for pairing via QR code exchange.
     *
     * @property serviceName The mDNS service name to filter for (e.g. "studio-XXXXXXXXXX").
     * @property password The pairing password encoded in the QR code.
     */
    data class Qr(
        val serviceName: String,
        val password: String
    ) : AdbPairingRequest {
        init {
            require(serviceName.isNotBlank()) { "Service name must not be blank" }
            require(password.isNotBlank()) { "Password must not be blank" }
        }

        override fun toString(): String = "Qr(serviceName=$serviceName, password=******)"
    }
}
