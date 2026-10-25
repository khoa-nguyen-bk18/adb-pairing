package com.devindie.zola.adb.pairing.api

import kotlinx.coroutines.flow.Flow

/**
 * Public, DI-neutral interface for ADB wireless pairing.
 *
 * Implementations manage mDNS service discovery, key persistence/crypto, SPAKE2 pairing handshake,
 * and endpoint resolution. All operations return cold [Flow]s where collection owns lifecycle and cancellation.
 */
interface AdbPairingManager {
    /**
     * Executes the pairing flow according to the given [request].
     *
     * Collection initiates discovery and handshake; cancelling the collection aborts
     * in-flight operations and cleans up sockets and mDNS registrations.
     */
    fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent>

    /**
     * Discovers local ADB pairing service port via NSD.
     * Useful for manual pairing flows where the port must be discovered before the user enters the code.
     */
    fun discoverPairingPort(config: AdbPairingConfig = AdbPairingConfig.DEFAULT): Flow<Int>
}
