package com.devindie.zola.adb.pairing.impl

import com.devindie.zola.adb.pairing.api.AdbConnectionEndpoint
import com.devindie.zola.adb.pairing.api.AdbPairingConfig
import com.devindie.zola.adb.pairing.api.AdbPairingEvent
import com.devindie.zola.adb.pairing.api.AdbPairingFailure
import com.devindie.zola.adb.pairing.api.AdbPairingRequest
import com.devindie.zola.adb.pairing.impl.crypto.AdbKey
import com.devindie.zola.adb.pairing.impl.crypto.AdbKeyProvider
import com.devindie.zola.adb.pairing.impl.nsd.AdbNsdDiscovery
import com.devindie.zola.adb.pairing.impl.nsd.DiscoveredService
import com.devindie.zola.adb.pairing.impl.pairing.AdbInvalidPairingCodeException
import com.devindie.zola.adb.pairing.impl.pairing.AdbPairingEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AdbPairingManagerImplTest {

    private class FakeAdbKeyProvider : AdbKeyProvider {
        override fun getOrCreateKey(name: String): AdbKey = AdbKey(name)
    }

    private class FakeNsdDiscovery : AdbNsdDiscovery {
        var pairingDelayMs: Long = 0
        var connectDelayMs: Long = 0
        var pairingService: DiscoveredService? = DiscoveredService("127.0.0.1", 41235, "studio-test")
        var connectService: DiscoveredService? = DiscoveredService("127.0.0.1", 38765, "adb-connect")
        var cancelled = false

        override fun discover(serviceType: String, serviceNameFilter: String?): Flow<DiscoveredService> = flow {
            try {
                if (serviceType == AdbNsdDiscovery.TLS_PAIRING) {
                    if (pairingDelayMs > 0) delay(pairingDelayMs)
                    pairingService?.let { emit(it) }
                } else if (serviceType == AdbNsdDiscovery.TLS_CONNECT) {
                    if (connectDelayMs > 0) delay(connectDelayMs)
                    connectService?.let { emit(it) }
                }
            } finally {
                cancelled = true
            }
        }
    }

    private class FakePairingEngine : AdbPairingEngine {
        var result: Boolean = true
        var exceptionToThrow: Throwable? = null
        var delayMs: Long = 0

        override suspend fun pair(host: String, port: Int, code: String, key: AdbKey): Boolean {
            if (delayMs > 0) delay(delayMs)
            exceptionToThrow?.let { throw it }
            return result
        }
    }

    @Test
    fun testSuccessfulQrPairing_eventOrderingAndCompletion() = runTest {
        val nsd = FakeNsdDiscovery()
        val engine = FakePairingEngine()
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = engine,
            currentApiLevel = 34
        )

        val request = AdbPairingRequest.Qr(serviceName = "studio-test", password = "secretPassword")
        val events = manager.pair(request).toList()

        assertEquals(5, events.size)
        assertTrue(events[0] is AdbPairingEvent.SearchingForPairingService)

        val serviceFound = events[1] as AdbPairingEvent.PairingServiceFound
        assertEquals(41235, serviceFound.port)
        assertEquals("studio-test", serviceFound.serviceName)

        assertTrue(events[2] is AdbPairingEvent.PairingInProgress)
        assertTrue(events[3] is AdbPairingEvent.SearchingForConnectionService)

        val success = events[4] as AdbPairingEvent.Success
        assertEquals(AdbConnectionEndpoint("127.0.0.1", 38765), success.result.connectionEndpoint)
    }

    @Test
    fun testSuccessfulManualPairingWithPort_skipsDiscovery() = runTest {
        val nsd = FakeNsdDiscovery()
        val engine = FakePairingEngine()
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = engine,
            currentApiLevel = 34
        )

        val request = AdbPairingRequest.Manual(pairingCode = "123456", port = 42424)
        val events = manager.pair(request).toList()

        assertEquals(4, events.size)
        val serviceFound = events[0] as AdbPairingEvent.PairingServiceFound
        assertEquals(42424, serviceFound.port)

        assertTrue(events[1] is AdbPairingEvent.PairingInProgress)
        assertTrue(events[2] is AdbPairingEvent.SearchingForConnectionService)

        val success = events[3] as AdbPairingEvent.Success
        assertEquals(38765, success.result.connectionEndpoint.port)
    }

    @Test
    fun testInvalidPairingCode_emitsInvalidPairingCodeFailure() = runTest {
        val nsd = FakeNsdDiscovery()
        val engine = FakePairingEngine().apply {
            exceptionToThrow = AdbInvalidPairingCodeException()
        }
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = engine,
            currentApiLevel = 34
        )

        val request = AdbPairingRequest.Manual(pairingCode = "000000", port = 42424)
        val events = manager.pair(request).toList()

        assertEquals(3, events.size)
        assertTrue(events[0] is AdbPairingEvent.PairingServiceFound)
        assertTrue(events[1] is AdbPairingEvent.PairingInProgress)

        val failed = events[2] as AdbPairingEvent.Failed
        assertTrue(failed.failure is AdbPairingFailure.InvalidPairingCode)
    }

    @Test
    fun testDiscoveryTimeout_emitsTimeoutFailure() = runTest {
        val nsd = FakeNsdDiscovery().apply {
            pairingDelayMs = 500
        }
        val engine = FakePairingEngine()
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = engine,
            currentApiLevel = 34
        )

        val request = AdbPairingRequest.Qr(
            serviceName = "studio-test",
            password = "pass",
            config = AdbPairingConfig(discoveryTimeoutMs = 50)
        )
        val events = manager.pair(request).toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is AdbPairingEvent.SearchingForPairingService)

        val failed = events[1] as AdbPairingEvent.Failed
        val timeout = failed.failure as AdbPairingFailure.Timeout
        assertEquals(AdbPairingFailure.Timeout.Phase.PAIRING_DISCOVERY, timeout.phase)
        assertEquals(50L, timeout.timeoutMs)
    }

    @Test
    fun testHandshakeTimeout_emitsTimeoutFailure() = runTest {
        val nsd = FakeNsdDiscovery()
        val engine = FakePairingEngine().apply {
            delayMs = 500
        }
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = engine,
            currentApiLevel = 34
        )

        val request = AdbPairingRequest.Manual(
            pairingCode = "123456",
            port = 40000,
            config = AdbPairingConfig(pairingTimeoutMs = 50)
        )
        val events = manager.pair(request).toList()

        assertEquals(3, events.size)
        assertTrue(events[0] is AdbPairingEvent.PairingServiceFound)
        assertTrue(events[1] is AdbPairingEvent.PairingInProgress)

        val failed = events[2] as AdbPairingEvent.Failed
        val timeout = failed.failure as AdbPairingFailure.Timeout
        assertEquals(AdbPairingFailure.Timeout.Phase.PAIRING_HANDSHAKE, timeout.phase)
    }

    @Test
    fun testConnectDiscoveryTimeout_emitsTimeoutFailure() = runTest {
        val nsd = FakeNsdDiscovery().apply {
            connectDelayMs = 500
        }
        val engine = FakePairingEngine()
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = engine,
            currentApiLevel = 34
        )

        val request = AdbPairingRequest.Manual(
            pairingCode = "123456",
            port = 40000,
            config = AdbPairingConfig(connectDiscoveryTimeoutMs = 50)
        )
        val events = manager.pair(request).toList()

        assertEquals(4, events.size)
        assertTrue(events[0] is AdbPairingEvent.PairingServiceFound)
        assertTrue(events[1] is AdbPairingEvent.PairingInProgress)
        assertTrue(events[2] is AdbPairingEvent.SearchingForConnectionService)

        val failed = events[3] as AdbPairingEvent.Failed
        val timeout = failed.failure as AdbPairingFailure.Timeout
        assertEquals(AdbPairingFailure.Timeout.Phase.CONNECT_DISCOVERY, timeout.phase)
    }

    @Test
    fun testUnsupportedPlatformVersion_belowAndroid11() = runTest {
        val nsd = FakeNsdDiscovery()
        val engine = FakePairingEngine()
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = engine,
            currentApiLevel = 29 // Android 10
        )

        val request = AdbPairingRequest.Manual(pairingCode = "123456", port = 40000)
        val events = manager.pair(request).toList()

        assertEquals(1, events.size)
        val failed = events[0] as AdbPairingEvent.Failed
        val unsupported = failed.failure as AdbPairingFailure.UnsupportedPlatformVersion
        assertEquals(29, unsupported.currentApi)
        assertEquals(30, unsupported.minRequiredApi)
    }

    @Test
    fun testColdFlow_cancellationCleansUp() = runTest {
        val nsd = FakeNsdDiscovery().apply {
            pairingDelayMs = 5000
        }
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = FakePairingEngine(),
            currentApiLevel = 34
        )

        val job = launch {
            manager.pair(AdbPairingRequest.Qr("studio-test", "pass")).collect {}
        }
        delay(50)
        job.cancel()
        delay(50)

        assertTrue(nsd.cancelled)
    }
}
