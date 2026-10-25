package io.github.khoanguyenbk18.adbpairing.impl

import android.content.Context
import io.github.khoanguyenbk18.adbpairing.api.AdbConnectionEndpoint
import io.github.khoanguyenbk18.adbpairing.api.AdbDiscoveryEvent
import io.github.khoanguyenbk18.adbpairing.api.AdbDiscoveryOptions
import io.github.khoanguyenbk18.adbpairing.api.AdbIdentity
import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingEvent
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingFailure
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingManager
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingRequest
import io.github.khoanguyenbk18.adbpairing.api.log
import io.github.khoanguyenbk18.adbpairing.impl.crypto.AdbKey
import io.github.khoanguyenbk18.adbpairing.impl.crypto.AdbKeyProvider
import io.github.khoanguyenbk18.adbpairing.impl.crypto.DefaultAdbKeyProvider
import io.github.khoanguyenbk18.adbpairing.impl.crypto.PreferenceAdbKeyStore
import io.github.khoanguyenbk18.adbpairing.impl.nsd.AdbNsdDiscovery
import io.github.khoanguyenbk18.adbpairing.impl.nsd.AndroidAdbNsdDiscovery
import io.github.khoanguyenbk18.adbpairing.impl.pairing.AdbInvalidPairingCodeException
import io.github.khoanguyenbk18.adbpairing.impl.pairing.AdbPairingEngine
import io.github.khoanguyenbk18.adbpairing.impl.pairing.DefaultAdbPairingEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.ConnectException

private const val TAG = "AdbPairingManager"

internal class AdbPairingManagerImpl internal constructor(
    private val keyProvider: AdbKeyProvider,
    private val nsdDiscovery: AdbNsdDiscovery,
    private val pairingEngine: AdbPairingEngine,
    private val options: AdbPairingOptions
) : AdbPairingManager {

    constructor(context: Context, options: AdbPairingOptions) : this(
        keyProvider = DefaultAdbKeyProvider(
            PreferenceAdbKeyStore(context.getSharedPreferences("adb_pairing", Context.MODE_PRIVATE))
        ),
        nsdDiscovery = AndroidAdbNsdDiscovery(context, options.logger),
        pairingEngine = DefaultAdbPairingEngine(options.logger),
        options = options
    )

    override suspend fun getIdentity(): AdbIdentity =
        keyProvider.getOrCreateKey(options.identityAlias)

    override fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent> = flow {
        options.logger.log(AdbLogger.DEBUG, TAG, "Starting pairing flow for request=$request")

        // Step 1: Pairing Port Resolution
        val pairingPort: Int
        when (request) {
            is AdbPairingRequest.Manual -> {
                val directPort = request.port
                if (directPort != null && directPort > 0) {
                    pairingPort = directPort
                    emit(
                        AdbPairingEvent.PairingServiceFound(
                            host = "127.0.0.1",
                            port = pairingPort
                        )
                    )
                } else {
                    emit(AdbPairingEvent.SearchingForPairingService)
                    val discovered = try {
                        withTimeout(options.discoveryTimeoutMs) {
                            nsdDiscovery.discover(AdbNsdDiscovery.TLS_PAIRING).first()
                        }
                    } catch (e: CancellationException) {
                        if (e is TimeoutCancellationException) {
                            emit(
                                AdbPairingEvent.Failed(
                                    AdbPairingFailure.Timeout(
                                        phase = AdbPairingFailure.Timeout.Phase.PAIRING_DISCOVERY,
                                        timeoutMs = options.discoveryTimeoutMs
                                    )
                                )
                            )
                            return@flow
                        }
                        throw e
                    } catch (e: Exception) {
                        emit(
                            AdbPairingEvent.Failed(
                                AdbPairingFailure.DiscoveryFailed(
                                    serviceType = AdbNsdDiscovery.TLS_PAIRING,
                                    reason = e.message,
                                    cause = e
                                )
                            )
                        )
                        return@flow
                    }
                    pairingPort = discovered.port
                    emit(
                        AdbPairingEvent.PairingServiceFound(
                            host = "127.0.0.1",
                            port = pairingPort,
                            serviceName = discovered.serviceName
                        )
                    )
                }
            }

            is AdbPairingRequest.Qr -> {
                emit(AdbPairingEvent.SearchingForPairingService)
                val discovered = try {
                    withTimeout(options.discoveryTimeoutMs) {
                        nsdDiscovery.discover(
                            serviceType = AdbNsdDiscovery.TLS_PAIRING,
                            serviceNameFilter = request.serviceName
                        ).first()
                    }
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException) {
                        emit(
                            AdbPairingEvent.Failed(
                                AdbPairingFailure.Timeout(
                                    phase = AdbPairingFailure.Timeout.Phase.PAIRING_DISCOVERY,
                                    timeoutMs = options.discoveryTimeoutMs
                                )
                            )
                        )
                        return@flow
                    }
                    throw e
                } catch (e: Exception) {
                    emit(
                        AdbPairingEvent.Failed(
                            AdbPairingFailure.DiscoveryFailed(
                                serviceType = AdbNsdDiscovery.TLS_PAIRING,
                                reason = e.message,
                                cause = e
                            )
                        )
                    )
                    return@flow
                }
                pairingPort = discovered.port
                emit(
                    AdbPairingEvent.PairingServiceFound(
                        host = "127.0.0.1",
                        port = pairingPort,
                        serviceName = discovered.serviceName
                    )
                )
            }
        }

        // Step 2: Pairing Handshake
        emit(AdbPairingEvent.PairingInProgress)

        val key: AdbKey = try {
            keyProvider.getOrCreateKey(options.identityAlias)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            options.logger.log(AdbLogger.ERROR, TAG, "Key retrieval failed: ${e.message}", e)
            emit(
                AdbPairingEvent.Failed(
                    AdbPairingFailure.KeyStoreError(message = e.message, cause = e)
                )
            )
            return@flow
        }

        val secret = when (request) {
            is AdbPairingRequest.Qr -> request.password
            is AdbPairingRequest.Manual -> request.pairingCode
        }

        val handshakeSuccess = try {
            withTimeout(options.pairingTimeoutMs) {
                pairingEngine.pair(
                    port = pairingPort,
                    code = secret,
                    key = key,
                    timeoutMs = options.pairingTimeoutMs
                )
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                emit(
                    AdbPairingEvent.Failed(
                        AdbPairingFailure.Timeout(
                            phase = AdbPairingFailure.Timeout.Phase.PAIRING_HANDSHAKE,
                            timeoutMs = options.pairingTimeoutMs
                        )
                    )
                )
                return@flow
            }
            throw e
        } catch (_: AdbInvalidPairingCodeException) {
            emit(AdbPairingEvent.Failed(AdbPairingFailure.InvalidPairingCode))
            return@flow
        } catch (e: ConnectException) {
            emit(
                AdbPairingEvent.Failed(
                    AdbPairingFailure.ConnectionFailed(message = e.message, cause = e)
                )
            )
            return@flow
        } catch (e: IOException) {
            emit(
                AdbPairingEvent.Failed(
                    AdbPairingFailure.ConnectionFailed(message = e.message, cause = e)
                )
            )
            return@flow
        } catch (e: Exception) {
            emit(
                AdbPairingEvent.Failed(
                    AdbPairingFailure.Unexpected(message = e.message, cause = e)
                )
            )
            return@flow
        }

        if (!handshakeSuccess) {
            emit(
                AdbPairingEvent.Failed(
                    AdbPairingFailure.ConnectionFailed(message = "Pairing handshake rejected by daemon")
                )
            )
            return@flow
        }

        // Step 3: Discover TLS connection endpoint
        emit(AdbPairingEvent.SearchingForConnectionService)

        val connectService = try {
            withTimeout(options.connectDiscoveryTimeoutMs) {
                nsdDiscovery.discover(serviceType = AdbNsdDiscovery.TLS_CONNECT).first()
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                emit(
                    AdbPairingEvent.Failed(
                        AdbPairingFailure.Timeout(
                            phase = AdbPairingFailure.Timeout.Phase.CONNECT_DISCOVERY,
                            timeoutMs = options.connectDiscoveryTimeoutMs
                        )
                    )
                )
                return@flow
            }
            throw e
        } catch (e: Exception) {
            emit(
                AdbPairingEvent.Failed(
                    AdbPairingFailure.DiscoveryFailed(
                        serviceType = AdbNsdDiscovery.TLS_CONNECT,
                        reason = e.message,
                        cause = e
                    )
                )
            )
            return@flow
        }

        val endpoint = AdbConnectionEndpoint(
            host = "127.0.0.1",
            port = connectService.port
        )
        emit(AdbPairingEvent.Success(endpoint))
    }

    override fun discoverPairingEndpoints(options: AdbDiscoveryOptions): Flow<AdbDiscoveryEvent> = flow {
        try {
            nsdDiscovery.discover(
                serviceType = AdbNsdDiscovery.TLS_PAIRING,
                serviceNameFilter = options.serviceNameFilter
            ).collect { service ->
                emit(
                    AdbDiscoveryEvent.Discovered(
                        endpoint = AdbConnectionEndpoint(host = service.host, port = service.port),
                        serviceName = service.serviceName
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AdbDiscoveryEvent.Failed(reason = e.message, cause = e))
        }
    }

    override fun discoverConnectionEndpoints(): Flow<AdbConnectionEndpoint> =
        nsdDiscovery.discover(serviceType = AdbNsdDiscovery.TLS_CONNECT).map {
            AdbConnectionEndpoint(host = it.host, port = it.port)
        }
}
