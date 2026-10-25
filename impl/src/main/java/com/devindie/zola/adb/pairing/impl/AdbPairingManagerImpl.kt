package com.devindie.zola.adb.pairing.impl

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.devindie.zola.adb.pairing.api.AdbConnectionEndpoint
import com.devindie.zola.adb.pairing.api.AdbPairingConfig
import com.devindie.zola.adb.pairing.api.AdbPairingEvent
import com.devindie.zola.adb.pairing.api.AdbPairingFailure
import com.devindie.zola.adb.pairing.api.AdbPairingManager
import com.devindie.zola.adb.pairing.api.AdbPairingRequest
import com.devindie.zola.adb.pairing.api.AdbPairingResult
import com.devindie.zola.adb.pairing.impl.crypto.AdbKey
import com.devindie.zola.adb.pairing.impl.crypto.AdbKeyProvider
import com.devindie.zola.adb.pairing.impl.crypto.PreferenceAdbKeyProvider
import com.devindie.zola.adb.pairing.impl.nsd.AdbNsdDiscovery
import com.devindie.zola.adb.pairing.impl.nsd.AndroidAdbNsdDiscovery
import com.devindie.zola.adb.pairing.impl.nsd.DiscoveredService
import com.devindie.zola.adb.pairing.impl.pairing.AdbInvalidPairingCodeException
import com.devindie.zola.adb.pairing.impl.pairing.AdbPairingEngine
import com.devindie.zola.adb.pairing.impl.pairing.DefaultAdbPairingEngine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.ConnectException

private const val TAG = "AdbPairingManager"

class AdbPairingManagerImpl internal constructor(
    private val keyProvider: AdbKeyProvider,
    private val nsdDiscovery: AdbNsdDiscovery,
    private val pairingEngine: AdbPairingEngine = DefaultAdbPairingEngine(),
    private val currentApiLevel: Int = Build.VERSION.SDK_INT
) : AdbPairingManager {

    constructor(context: Context) : this(
        context = context,
        preferences = context.getSharedPreferences("adb_pairing", Context.MODE_PRIVATE)
    )

    constructor(context: Context, preferences: SharedPreferences) : this(
        keyProvider = PreferenceAdbKeyProvider(preferences),
        nsdDiscovery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AndroidAdbNsdDiscovery(context)
        } else {
            object : AdbNsdDiscovery {
                override fun discover(
                    serviceType: String,
                    serviceNameFilter: String?
                ): Flow<DiscoveredService> = emptyFlow()
            }
        },
        pairingEngine = DefaultAdbPairingEngine(),
        currentApiLevel = Build.VERSION.SDK_INT
    )

    override fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent> = flow {
        Log.d(TAG, "Starting pairing flow for request=$request")

        if (currentApiLevel < 30) {
            Log.w(TAG, "Unsupported Android version: $currentApiLevel (requires 30+)")
            emit(
                AdbPairingEvent.Failed(
                    AdbPairingFailure.UnsupportedPlatformVersion(
                        currentApi = currentApiLevel,
                        minRequiredApi = 30
                    )
                )
            )
            return@flow
        }

        // Step 1: Pairing Port Resolution
        val pairingPort: Int
        when (request) {
            is AdbPairingRequest.Manual -> {
                val directPort = request.port
                if (directPort != null && directPort > 0) {
                    pairingPort = directPort
                    emit(
                        AdbPairingEvent.PairingServiceFound(
                            host = request.config.host,
                            port = pairingPort
                        )
                    )
                } else {
                    emit(AdbPairingEvent.SearchingForPairingService)
                    val discovered = try {
                        withTimeout(request.config.discoveryTimeoutMs) {
                            nsdDiscovery.discover(AdbNsdDiscovery.TLS_PAIRING).first()
                        }
                    } catch (_: TimeoutCancellationException) {
                        emit(
                            AdbPairingEvent.Failed(
                                AdbPairingFailure.Timeout(
                                    phase = AdbPairingFailure.Timeout.Phase.PAIRING_DISCOVERY,
                                    timeoutMs = request.config.discoveryTimeoutMs
                                )
                            )
                        )
                        return@flow
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
                            host = request.config.host,
                            port = pairingPort,
                            serviceName = discovered.serviceName
                        )
                    )
                }
            }

            is AdbPairingRequest.Qr -> {
                emit(AdbPairingEvent.SearchingForPairingService)
                val discovered = try {
                    withTimeout(request.config.discoveryTimeoutMs) {
                        nsdDiscovery.discover(
                            serviceType = AdbNsdDiscovery.TLS_PAIRING,
                            serviceNameFilter = request.serviceName
                        ).first()
                    }
                } catch (_: TimeoutCancellationException) {
                    emit(
                        AdbPairingEvent.Failed(
                            AdbPairingFailure.Timeout(
                                phase = AdbPairingFailure.Timeout.Phase.PAIRING_DISCOVERY,
                                timeoutMs = request.config.discoveryTimeoutMs
                            )
                        )
                    )
                    return@flow
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
                        host = request.config.host,
                        port = pairingPort,
                        serviceName = discovered.serviceName
                    )
                )
            }
        }

        // Step 2: Pairing Handshake
        emit(AdbPairingEvent.PairingInProgress)

        val key: AdbKey = try {
            keyProvider.getOrCreateKey(request.config.keyAlias)
        } catch (e: Throwable) {
            Log.e(TAG, "Key retrieval failed: ${e.message}", e)
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
            withTimeout(request.config.pairingTimeoutMs) {
                pairingEngine.pair(
                    host = request.config.host,
                    port = pairingPort,
                    code = secret,
                    key = key
                )
            }
        } catch (_: TimeoutCancellationException) {
            emit(
                AdbPairingEvent.Failed(
                    AdbPairingFailure.Timeout(
                        phase = AdbPairingFailure.Timeout.Phase.PAIRING_HANDSHAKE,
                        timeoutMs = request.config.pairingTimeoutMs
                    )
                )
            )
            return@flow
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
            withTimeout(request.config.connectDiscoveryTimeoutMs) {
                nsdDiscovery.discover(serviceType = AdbNsdDiscovery.TLS_CONNECT).first()
            }
        } catch (_: TimeoutCancellationException) {
            emit(
                AdbPairingEvent.Failed(
                    AdbPairingFailure.Timeout(
                        phase = AdbPairingFailure.Timeout.Phase.CONNECT_DISCOVERY,
                        timeoutMs = request.config.connectDiscoveryTimeoutMs
                    )
                )
            )
            return@flow
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
            host = request.config.host,
            port = connectService.port
        )
        emit(AdbPairingEvent.Success(AdbPairingResult(endpoint)))
    }

    override fun discoverPairingPort(config: AdbPairingConfig): Flow<Int> =
        nsdDiscovery.discover(AdbNsdDiscovery.TLS_PAIRING).map { it.port }
}
