package com.devindie.zola.adb.pairing.api

/**
 * Discovered endpoint where the ADB daemon is listening for TLS connections.
 *
 * @property host The host address (typically "127.0.0.1").
 * @property port The discovered connection port.
 */
data class AdbConnectionEndpoint(
    val host: String,
    val port: Int
)

/**
 * Outcome of a successful ADB pairing flow, concluding with the discovered TLS connection endpoint.
 *
 * @property connectionEndpoint The discovered connection host and port for subsequent ADB operations.
 */
data class AdbPairingResult(
    val connectionEndpoint: AdbConnectionEndpoint
)
