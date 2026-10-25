package com.devindie.zola.adb.pairing.impl.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

private const val TAG = "AdbNsdDiscovery"

data class DiscoveredService(
    val host: String,
    val port: Int,
    val serviceName: String
)

interface AdbNsdDiscovery {
    fun discover(
        serviceType: String,
        serviceNameFilter: String? = null
    ): Flow<DiscoveredService>

    companion object {
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
    }
}

@RequiresApi(Build.VERSION_CODES.R)
class AndroidAdbNsdDiscovery(
    private val context: Context
) : AdbNsdDiscovery {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    override fun discover(
        serviceType: String,
        serviceNameFilter: String?
    ): Flow<DiscoveredService> = callbackFlow {
        var isDiscoveryActive = false

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "onDiscoveryStarted: $regType")
                isDiscoveryActive = true
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "onStartDiscoveryFailed: $serviceType, code=$errorCode")
                isDiscoveryActive = false
                close(IOException("NSD discovery start failed: $errorCode"))
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "onDiscoveryStopped: $serviceType")
                isDiscoveryActive = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "onStopDiscoveryFailed: $serviceType, code=$errorCode")
                isDiscoveryActive = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "onServiceFound: ${serviceInfo.serviceName}")
                try {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "onResolveFailed: ${info.serviceName}, code=$errorCode")
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            Log.d(TAG, "onServiceResolved: ${resolved.serviceName} at port ${resolved.port}")
                            if (serviceNameFilter != null && resolved.serviceName != serviceNameFilter) {
                                Log.d(TAG, "Skipping ${resolved.serviceName}, waiting for $serviceNameFilter")
                                return
                            }

                            val isLocalHost = runCatching {
                                NetworkInterface.getNetworkInterfaces()
                                    ?.asSequence()
                                    ?.any { iface ->
                                        iface.inetAddresses.asSequence().any { it.hostAddress == resolved.host.hostAddress }
                                    } == true
                            }.getOrDefault(false)

                            if (isLocalHost && isPortBound(resolved.port)) {
                                val item = DiscoveredService(
                                    host = resolved.host.hostAddress ?: "127.0.0.1",
                                    port = resolved.port,
                                    serviceName = resolved.serviceName
                                )
                                trySend(item)
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "resolveService threw: ${e.message}")
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "onServiceLost: ${serviceInfo.serviceName}")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            if (isDiscoveryActive) {
                try {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                } catch (e: Exception) {
                    Log.w(TAG, "stopServiceDiscovery failed during cleanup: ${e.message}")
                }
            }
        }
    }

    private fun isPortBound(port: Int): Boolean {
        return try {
            ServerSocket().use {
                it.bind(InetSocketAddress("127.0.0.1", port), 1)
                false // successfully bound -> port was NOT in use
            }
        } catch (_: IOException) {
            true // binding failed -> port is in use by daemon
        }
    }
}
