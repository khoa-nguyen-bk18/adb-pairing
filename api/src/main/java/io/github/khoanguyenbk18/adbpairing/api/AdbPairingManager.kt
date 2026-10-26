package io.github.khoanguyenbk18.adbpairing.api

import kotlinx.coroutines.flow.Flow

/**
 * Public, DI-neutral interface for ADB wireless pairing and discovery operations.
 *
 * Implementations manage mDNS service discovery, key persistence/crypto, SPAKE2 pairing handshake,
 * and endpoint resolution. All operations return cold [Flow]s where collection owns lifecycle and cancellation.
 */
interface AdbPairingManager {
    /**
     * Retrieves or generates the local ADB cryptographic identity.
     */
    suspend fun getIdentity(): AdbIdentity

    /**
     * Synchronously retrieves or generates the local ADB cryptographic identity.
     */
    fun getIdentitySync(): AdbIdentity

    /**
     * Executes the pairing flow according to the given [request].
     *
     * Collection initiates discovery and handshake; cancelling the collection aborts
     * in-flight operations and cleans up sockets and mDNS registrations cleanly.
     */
    fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent>

    /**
     * Discovers endpoints advertising the ADB pairing service (_adb-tls-pairing._tcp).
     */
    fun discoverPairingEndpoints(
        options: AdbDiscoveryOptions = AdbDiscoveryOptions()
    ): Flow<AdbDiscoveryEvent>

    /**
     * Discovers endpoints advertising the ADB TLS connect service (_adb-tls-connect._tcp).
     */
    fun discoverConnectionEndpoints(): Flow<AdbConnectionEndpoint>
}
