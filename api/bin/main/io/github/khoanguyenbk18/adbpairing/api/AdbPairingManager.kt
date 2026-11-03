package io.github.khoanguyenbk18.adbpairing.api

import kotlinx.coroutines.CancellationException
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
     * Shorthand for pairing with a 6-digit manual [pairingCode] and optional [port].
     */
    fun pair(pairingCode: String, port: Int? = null): Flow<AdbPairingEvent> =
        pair(AdbPairingRequest.Manual(pairingCode = pairingCode.trim(), port = port))

    /**
     * Shorthand for pairing with a generated [QrPairingSession].
     */
    fun pair(session: QrPairingSession): Flow<AdbPairingEvent> =
        pair(AdbPairingRequest.Qr(serviceName = session.serviceName, password = session.password))

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

/**
 * Collects [AdbPairingManager.pair] until a terminal event is reached and returns the resolved
 * [AdbConnectionEndpoint] wrapped in [Result], or fails with [AdbPairingException].
 */
suspend fun AdbPairingManager.awaitPairing(
    request: AdbPairingRequest,
    onProgress: (AdbPairingEvent) -> Unit = {}
): Result<AdbConnectionEndpoint> {
    return try {
        var result: Result<AdbConnectionEndpoint>? = null
        pair(request).collect { event ->
            when (event) {
                is AdbPairingEvent.Success -> result = Result.success(event.endpoint)
                is AdbPairingEvent.Failed -> result = Result.failure(AdbPairingException(event.failure))
                else -> onProgress(event)
            }
        }
        result ?: Result.failure(
            AdbPairingException(
                AdbPairingFailure.Unexpected("Pairing flow completed without emitting a terminal event")
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/**
 * One-call suspend helper to pair using a 6-digit [pairingCode] (and optional [port]).
 */
suspend fun AdbPairingManager.awaitPairing(
    pairingCode: String,
    port: Int? = null,
    onProgress: (AdbPairingEvent) -> Unit = {}
): Result<AdbConnectionEndpoint> {
    val request = try {
        AdbPairingRequest.Manual(pairingCode = pairingCode.trim(), port = port)
    } catch (e: Throwable) {
        return Result.failure(e)
    }
    return awaitPairing(request, onProgress)
}

/**
 * One-call suspend helper to pair using a [QrPairingSession].
 */
suspend fun AdbPairingManager.awaitPairing(
    session: QrPairingSession,
    onProgress: (AdbPairingEvent) -> Unit = {}
): Result<AdbConnectionEndpoint> =
    awaitPairing(
        request = AdbPairingRequest.Qr(serviceName = session.serviceName, password = session.password),
        onProgress = onProgress
    )
