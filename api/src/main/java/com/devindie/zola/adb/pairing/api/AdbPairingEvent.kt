package com.devindie.zola.adb.pairing.api

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
     *
     * @property host Host address of the pairing service.
     * @property port Port of the pairing service.
     * @property serviceName The discovered service name, if known.
     */
    data class PairingServiceFound(
        val host: String,
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
    data class Success(val result: AdbPairingResult) : AdbPairingEvent

    /**
     * Pairing failed. Terminal event.
     */
    data class Failed(val failure: AdbPairingFailure) : AdbPairingEvent
}
