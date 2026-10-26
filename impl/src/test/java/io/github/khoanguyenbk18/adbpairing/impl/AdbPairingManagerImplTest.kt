package io.github.khoanguyenbk18.adbpairing.impl

import io.github.khoanguyenbk18.adbpairing.api.AdbConnectionEndpoint
import io.github.khoanguyenbk18.adbpairing.api.AdbDiscoveryEvent
import io.github.khoanguyenbk18.adbpairing.api.AdbDiscoveryOptions
import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingEvent
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingFailure
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingRequest
import io.github.khoanguyenbk18.adbpairing.impl.crypto.AdbKey
import io.github.khoanguyenbk18.adbpairing.impl.crypto.AdbKeyProvider
import io.github.khoanguyenbk18.adbpairing.impl.nsd.AdbNsdDiscovery
import io.github.khoanguyenbk18.adbpairing.impl.nsd.DiscoveredService
import io.github.khoanguyenbk18.adbpairing.impl.pairing.AdbInvalidPairingCodeException
import io.github.khoanguyenbk18.adbpairing.impl.pairing.AdbPairingEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class AdbPairingManagerImplTest {

    private class FakeAdbKeyProvider : AdbKeyProvider {
        override fun getOrCreateKey(alias: String): AdbKey = AdbKey(alias)
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

        override suspend fun pair(port: Int, code: String, key: AdbKey, timeoutMs: Long): Boolean {
            if (delayMs > 0) delay(delayMs)
            exceptionToThrow?.let { throw it }
            return result
        }
    }

    private class CapturingLogger : AdbLogger {
        val messages = CopyOnWriteArrayList<String>()
        override fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
            messages.add(message)
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
            options = AdbPairingOptions()
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
        assertEquals(AdbConnectionEndpoint("127.0.0.1", 38765), success.endpoint)
    }

    @Test
    fun testSuccessfulManualPairingWithPort_skipsDiscovery() = runTest {
        val nsd = FakeNsdDiscovery()
        val engine = FakePairingEngine()
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = engine,
            options = AdbPairingOptions()
        )

        val request = AdbPairingRequest.Manual(pairingCode = "123456", port = 42424)
        val events = manager.pair(request).toList()

        assertEquals(4, events.size)
        val serviceFound = events[0] as AdbPairingEvent.PairingServiceFound
        assertEquals(42424, serviceFound.port)

        assertTrue(events[1] is AdbPairingEvent.PairingInProgress)
        assertTrue(events[2] is AdbPairingEvent.SearchingForConnectionService)

        val success = events[3] as AdbPairingEvent.Success
        assertEquals(38765, success.endpoint.port)
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
            options = AdbPairingOptions()
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
            options = AdbPairingOptions(discoveryTimeoutMs = 50)
        )

        val request = AdbPairingRequest.Qr(
            serviceName = "studio-test",
            password = "pass"
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
            options = AdbPairingOptions(pairingTimeoutMs = 50)
        )

        val request = AdbPairingRequest.Manual(
            pairingCode = "123456",
            port = 40000
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
            options = AdbPairingOptions(connectDiscoveryTimeoutMs = 50)
        )

        val request = AdbPairingRequest.Manual(
            pairingCode = "123456",
            port = 40000
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
    fun testColdFlow_cancellationCleansUpWithoutFailureEvent() = runTest {
        val nsd = FakeNsdDiscovery().apply {
            pairingDelayMs = 5000
        }
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = FakePairingEngine(),
            options = AdbPairingOptions()
        )

        val events = mutableListOf<AdbPairingEvent>()
        val job = launch {
            manager.pair(AdbPairingRequest.Qr("studio-test", "pass")).collect {
                events.add(it)
            }
        }
        delay(50)
        job.cancel()
        delay(50)

        assertTrue(nsd.cancelled)
        assertFalse(events.any { it is AdbPairingEvent.Failed })
    }

    @Test
    fun testSecretsNeverLogged() = runTest {
        val logger = CapturingLogger()
        val nsd = FakeNsdDiscovery()
        val engine = FakePairingEngine()
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = engine,
            options = AdbPairingOptions(logger = logger)
        )

        val secretCode = "654321"
        val request = AdbPairingRequest.Manual(pairingCode = secretCode, port = 42424)
        manager.pair(request).toList()

        assertTrue(logger.messages.isNotEmpty())
        for (msg in logger.messages) {
            assertFalse("Secret pairing code leaked in logs: $msg", msg.contains(secretCode))
        }
    }

    @Test
    fun testDiscoverPairingEndpoints() = runTest {
        val nsd = FakeNsdDiscovery()
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = FakePairingEngine(),
            options = AdbPairingOptions()
        )

        val events = manager.discoverPairingEndpoints(AdbDiscoveryOptions()).toList()
        assertEquals(1, events.size)
        val discovered = events[0] as AdbDiscoveryEvent.Discovered
        assertEquals(41235, discovered.endpoint.port)
        assertEquals("studio-test", discovered.serviceName)
    }

    @Test
    fun testDiscoverConnectionEndpoints() = runTest {
        val nsd = FakeNsdDiscovery()
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = nsd,
            pairingEngine = FakePairingEngine(),
            options = AdbPairingOptions()
        )

        val endpoints = manager.discoverConnectionEndpoints().toList()
        assertEquals(1, endpoints.size)
        assertEquals(38765, endpoints[0].port)
    }

    @Test
    fun testGetIdentitySync() {
        val manager = AdbPairingManagerImpl(
            keyProvider = FakeAdbKeyProvider(),
            nsdDiscovery = FakeNsdDiscovery(),
            pairingEngine = FakePairingEngine(),
            options = AdbPairingOptions(identityAlias = "test-sync-alias")
        )

        val identity = manager.getIdentitySync()
        assertEquals("test-sync-alias", identity.name)
    }
}
