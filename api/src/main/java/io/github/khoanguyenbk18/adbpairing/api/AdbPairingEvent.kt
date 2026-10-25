package io.github.khoanguyenbk18.adbpairing.api

/**
 * Events emitted by [AdbPairingManager.pair] during the lifecycle of a pairing request.
 */
sealed interface AdbPairingEvent {
    /**
     * Searching for the local ADB pairing mDNS service.
     */
    data object SearchingForPairingService : AdbPairingEvent

    /**
     * The ADB pairing service was discovered.
     */
    data class PairingServiceFound(
        val host: String = "127.0.0.1",
        val port: Int,
        val serviceName: String? = null
    ) : AdbPairingEvent

    /**
     * TLS handshake and SPAKE2 key exchange is currently in progress.
     */
    data object PairingInProgress : AdbPairingEvent

    /**
     * Pairing handshake succeeded; now discovering the TLS connect service to locate the final endpoint.
     */
    data object SearchingForConnectionService : AdbPairingEvent

    /**
     * Pairing completed successfully and connection endpoint is discovered. Terminal event.
     */
    data class Success(val endpoint: AdbConnectionEndpoint) : AdbPairingEvent

    /**
     * Pairing failed. Terminal event.
     */
    data class Failed(val failure: AdbPairingFailure) : AdbPairingEvent
}
