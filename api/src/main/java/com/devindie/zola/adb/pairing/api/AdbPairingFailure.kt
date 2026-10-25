package com.devindie.zola.adb.pairing.api

/**
 * Typed failure hierarchy for ADB pairing errors.
 */
sealed interface AdbPairingFailure {
    /**
     * Typed failure when running on Android versions below Android 11 (API 30),
     * where wireless pairing via TLS is unsupported.
     *
     * @property currentApi The current platform API level.
     * @property minRequiredApi The minimum required platform API level (30).
     */
    data class UnsupportedPlatformVersion(
        val currentApi: Int,
        val minRequiredApi: Int = 30
    ) : AdbPairingFailure

    /**
     * The pairing code or password was incorrect.
     */
    data object InvalidPairingCode : AdbPairingFailure

    /**
     * Failed to establish a socket or TLS connection to the pairing service.
     */
    data class ConnectionFailed(
        val message: String? = null,
        val cause: Throwable? = null
    ) : AdbPairingFailure

    /**
     * Failed to load or persist ADB keys in the keystore.
     */
    data class KeyStoreError(
        val message: String? = null,
        val cause: Throwable? = null
    ) : AdbPairingFailure

    /**
     * Service discovery failed or could not be initiated.
     */
    data class DiscoveryFailed(
        val serviceType: String,
        val reason: String? = null,
        val cause: Throwable? = null
    ) : AdbPairingFailure

    /**
     * An operation timed out.
     */
    data class Timeout(
        val phase: Phase,
        val timeoutMs: Long
    ) : AdbPairingFailure {
        enum class Phase {
            PAIRING_DISCOVERY,
            PAIRING_HANDSHAKE,
            CONNECT_DISCOVERY
        }
    }

    /**
     * An unexpected or unclassified failure occurred.
     */
    data class Unexpected(
        val message: String? = null,
        val cause: Throwable? = null
    ) : AdbPairingFailure
}
