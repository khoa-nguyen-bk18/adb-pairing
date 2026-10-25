# ADB Pairing Library Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the `adb-pairing` library into a robust, leak-free architecture with a pure Kotlin `:api` module, an internalized `:impl` module, an alias-aware keystore, cancellation-safe flows, secret redaction, minSdk 30, and clean release lint.

**Architecture:** Pure JVM contracts (`AdbIdentity`, `AdbPairingManager`, `AdbLogger`) in `:api` (`io.github.khoanguyenbk18.adbpairing.api`). Android implementation in `:impl` (`io.github.khoanguyenbk18.adbpairing.impl`) exposes only a single entry point `AdbPairing.create()`, with all other classes marked `internal`. Preserves JNI bridge `moe.shizuku.manager.adb.PairingContext` with precise ProGuard consumer rules.

**Tech Stack:** Kotlin 2.0.21, Kotlin Coroutines 1.8.1, Android Gradle Plugin 8.12.0, Conscrypt Android 2.5.3, BouncyCastle 1.80, JUnit 4.

**Spec:** [docs/superpowers/specs/2026-10-25-adb-pairing-refactor-design.md](file:///Users/khoa/PROJECTS/adb-pairing/docs/superpowers/specs/2026-10-25-adb-pairing-refactor-design.md)

## Global Constraints

- Root groupId: `io.github.khoanguyenbk18.adbpairing`
- Android `minSdk`: 30
- Gradle wrapper version: 8.13 (compatible with AGP 8.12.0)
- JNI package name `moe.shizuku.manager.adb.PairingContext` must remain unchanged for `libadb.so`
- Secrets (pairing codes, passwords) must never be logged or output in cleartext via `toString()`
- All broad `catch` blocks in coroutine flows must re-throw `CancellationException`
- All implementation classes in `:impl` must be `internal` except `AdbPairing` and `AdbPairingOptions`

---

### Task 1: Fix Gradle Wrapper & Clean Build Configuration

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `gradle/libs.versions.toml`
- Modify: `impl/build.gradle.kts`

**Interfaces:**
- Consumes: Existing Gradle setup
- Produces: Working AGP 8.12.0 build environment with minSdk 30 and no deprecated lifecycle dependency

- [ ] **Step 1: Revert Gradle wrapper to 8.13**

In `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: Update `gradle/libs.versions.toml` to remove lifecycle dependency**

Remove `lifecycle = "2.8.7"` and `androidx-lifecycle-livedata-core = { module = "androidx.lifecycle:lifecycle-livedata-core", version.ref = "lifecycle" }`.

- [ ] **Step 3: Update `impl/build.gradle.kts`**

Set `namespace = "io.github.khoanguyenbk18.adbpairing.impl"`, `minSdk = 30`, and remove `implementation(libs.androidx.lifecycle.livedata.core)`.

- [ ] **Step 4: Verify build environment**

Run: `./gradlew tasks`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/wrapper/gradle-wrapper.properties gradle/libs.versions.toml impl/build.gradle.kts
git commit -m "build: restore gradle 8.13, raise minSdk to 30, and remove lifecycle dependency"
```

---

### Task 2: Core API Contracts & Redacted Data Models (`:api`)

**Files:**
- Create: `api/src/main/java/io/github/khoanguyenbk18/adbpairing/api/AdbIdentity.kt`
- Create: `api/src/main/java/io/github/khoanguyenbk18/adbpairing/api/AdbLogger.kt`
- Create: `api/src/main/java/io/github/khoanguyenbk18/adbpairing/api/AdbDiscovery.kt`
- Create: `api/src/main/java/io/github/khoanguyenbk18/adbpairing/api/AdbPairingRequest.kt`
- Create: `api/src/main/java/io/github/khoanguyenbk18/adbpairing/api/AdbPairingEvent.kt`
- Create: `api/src/main/java/io/github/khoanguyenbk18/adbpairing/api/AdbPairingFailure.kt`
- Create: `api/src/main/java/io/github/khoanguyenbk18/adbpairing/api/QrPairingSession.kt`
- Create: `api/src/main/java/io/github/khoanguyenbk18/adbpairing/api/AdbPairingManager.kt`
- Delete: `api/src/main/java/com/devindie/zola/adb/pairing/api/*`
- Test: `api/src/test/java/io/github/khoanguyenbk18/adbpairing/api/AdbPairingApiTest.kt`

**Interfaces:**
- Consumes: None (root contracts)
- Produces: `AdbIdentity`, `AdbPairingManager`, `AdbLogger`, `AdbPairingRequest`, `AdbPairingEvent`, `AdbPairingFailure`, `AdbDiscoveryEvent`, `AdbConnectionEndpoint`, `QrPairingSession`

- [ ] **Step 1: Write failing unit tests for API contracts and secret masking**

Create `api/src/test/java/io/github/khoanguyenbk18/adbpairing/api/AdbPairingApiTest.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AdbPairingApiTest {

    @Test
    fun testManualRequest_validInput() {
        val request = AdbPairingRequest.Manual(pairingCode = "123456", port = 5555)
        assertEquals("123456", request.pairingCode)
        assertEquals(5555, request.port)
        assertTrue(request.toString().contains("******"))
        assertFalse(request.toString().contains("123456"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManualRequest_invalidLength_throws() {
        AdbPairingRequest.Manual(pairingCode = "12345")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManualRequest_nonDigits_throws() {
        AdbPairingRequest.Manual(pairingCode = "12345a")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManualRequest_invalidPort_throws() {
        AdbPairingRequest.Manual(pairingCode = "123456", port = 70000)
    }

    @Test
    fun testQrRequest_validInput() {
        val request = AdbPairingRequest.Qr(serviceName = "studio-test", password = "secretPassword")
        assertEquals("studio-test", request.serviceName)
        assertEquals("secretPassword", request.password)
        assertTrue(request.toString().contains("******"))
        assertFalse(request.toString().contains("secretPassword"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testQrRequest_blankServiceName_throws() {
        AdbPairingRequest.Qr(serviceName = "", password = "pass")
    }

    @Test
    fun testQrPairingSession_generateAndRedaction() {
        val session = QrPairingSession.generate()
        assertNotNull(session.sessionId)
        UUID.fromString(session.sessionId)
        assertTrue(session.serviceName.startsWith("studio-"))
        assertEquals(17, session.serviceName.length)
        assertEquals(20, session.password.length)
        assertTrue(session.toString().contains("******"))
        assertFalse(session.toString().contains(session.password))
        assertTrue(session.qrContent().contains(session.password))
    }
}
```

- [ ] **Step 2: Run test to verify compilation failure**

Run: `./gradlew :api:test`
Expected: Compilation failure because new types are not yet defined.

- [ ] **Step 3: Implement new API contracts**

Write:
1. `AdbIdentity.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.api

import javax.net.ssl.SSLContext

interface AdbIdentity {
    val name: String
    fun encodedPublicKey(): ByteArray
    fun sign(token: ByteArray): ByteArray
    fun createSslContext(): SSLContext
}
```

2. `AdbLogger.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.api

fun interface AdbLogger {
    fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null)

    companion object {
        const val VERBOSE = 2
        const val DEBUG = 3
        const val INFO = 4
        const val WARN = 5
        const val ERROR = 6
    }
}

object NoOpAdbLogger : AdbLogger {
    override fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {}
}
```

3. `AdbDiscovery.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.api

data class AdbConnectionEndpoint(
    val host: String = "127.0.0.1",
    val port: Int
)

data class AdbDiscoveryOptions(
    val timeoutMs: Long = 60_000L,
    val serviceNameFilter: String? = null
)

sealed interface AdbDiscoveryEvent {
    data class Discovered(val endpoint: AdbConnectionEndpoint, val serviceName: String) : AdbDiscoveryEvent
    data class Lost(val serviceName: String) : AdbDiscoveryEvent
    data class Failed(val reason: String?, val cause: Throwable? = null) : AdbDiscoveryEvent
}
```

4. `AdbPairingRequest.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.api

sealed interface AdbPairingRequest {
    data class Manual(
        val pairingCode: String,
        val port: Int? = null
    ) : AdbPairingRequest {
        init {
            require(pairingCode.length == 6 && pairingCode.all { it.isDigit() }) {
                "Pairing code must be exactly 6 decimal digits"
            }
            if (port != null) {
                require(port in 1..65535) { "Port must be in range 1..65535" }
            }
        }

        override fun toString(): String = "Manual(pairingCode=******, port=$port)"
    }

    data class Qr(
        val serviceName: String,
        val password: String
    ) : AdbPairingRequest {
        init {
            require(serviceName.isNotBlank()) { "Service name must not be blank" }
            require(password.isNotBlank()) { "Password must not be blank" }
        }

        override fun toString(): String = "Qr(serviceName=$serviceName, password=******)"
    }
}
```

5. `AdbPairingEvent.kt` & `AdbPairingFailure.kt`:
Recreate under package `io.github.khoanguyenbk18.adbpairing.api`. Include `Success(val endpoint: AdbConnectionEndpoint)` in `AdbPairingEvent`.

6. `QrPairingSession.kt`:
Recreate with masked `toString()` under `io.github.khoanguyenbk18.adbpairing.api`.

7. `AdbPairingManager.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.api

import kotlinx.coroutines.flow.Flow

interface AdbPairingManager {
    suspend fun getIdentity(): AdbIdentity

    fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent>

    fun discoverPairingEndpoints(
        options: AdbDiscoveryOptions = AdbDiscoveryOptions()
    ): Flow<AdbDiscoveryEvent>

    fun discoverConnectionEndpoints(): Flow<AdbConnectionEndpoint>
}
```

8. Delete old `api/src/main/java/com/devindie` and `api/src/test/java/com/devindie` trees.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test`
Expected: BUILD SUCCESSFUL (all unit tests in `:api` pass).

- [ ] **Step 5: Commit**

```bash
git add api/
git commit -m "feat(api): define clean AdbIdentity, validated requests, and masked credentials"
```

---

### Task 3: Alias-Aware Keystore & Identity Implementation (`:impl`)

**Files:**
- Create: `impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/crypto/AdbKey.kt`
- Create: `impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/crypto/AdbKeyStore.kt`
- Create: `impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/crypto/AdbKeyProvider.kt`
- Delete: `impl/src/main/java/com/devindie/zola/adb/pairing/impl/crypto/*`
- Test: `impl/src/test/java/io/github/khoanguyenbk18/adbpairing/impl/crypto/PreferenceAdbKeyStoreTest.kt`

**Interfaces:**
- Consumes: `AdbIdentity` from `:api`
- Produces: `internal class AdbKey : AdbIdentity`, `internal interface AdbKeyStore`, `internal class PreferenceAdbKeyStore`, `internal interface AdbKeyProvider`

- [ ] **Step 1: Write test for alias-aware PreferenceAdbKeyStore**

Create `impl/src/test/java/io/github/khoanguyenbk18/adbpairing/impl/crypto/PreferenceAdbKeyStoreTest.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.impl.crypto

import android.content.SharedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.HashMap

class PreferenceAdbKeyStoreTest {

    private class FakeSharedPreferences : SharedPreferences {
        val map = HashMap<String, Any?>()

        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun edit(): SharedPreferences.Editor = Editor()

        inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                map[key] = value
                return this
            }
            override fun commit(): Boolean = true
            override fun apply() {}
            override fun remove(key: String): SharedPreferences.Editor = this
            override fun clear(): SharedPreferences.Editor = this
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
        }

        override fun getAll(): MutableMap<String, *> = map
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun testDifferentAliases_storedIndependently() {
        val prefs = FakeSharedPreferences()
        val store = PreferenceAdbKeyStore(prefs)

        val keyA = byteArrayOf(1, 2, 3)
        val keyB = byteArrayOf(4, 5, 6)

        store.save("aliasA", keyA)
        store.save("aliasB", keyB)

        assertArrayEquals(keyA, store.load("aliasA"))
        assertArrayEquals(keyB, store.load("aliasB"))
        assertNull(store.load("aliasC"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails to compile**

Run: `./gradlew :impl:testDebugUnitTest --tests PreferenceAdbKeyStoreTest`
Expected: FAIL due to missing class.

- [ ] **Step 3: Implement alias-aware AdbKeyStore and AdbKey**

1. `AdbKeyStore.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.impl.crypto

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit

internal interface AdbKeyStore {
    fun load(alias: String): ByteArray?
    fun save(alias: String, bytes: ByteArray)
}

internal class PreferenceAdbKeyStore(
    private val preferences: SharedPreferences
) : AdbKeyStore {
    override fun load(alias: String): ByteArray? {
        val prefKey = "adbkey_$alias"
        if (!preferences.contains(prefKey)) return null
        val base64 = preferences.getString(prefKey, null) ?: return null
        return Base64.decode(base64, Base64.NO_WRAP)
    }

    override fun save(alias: String, bytes: ByteArray) {
        val prefKey = "adbkey_$alias"
        preferences.edit {
            putString(prefKey, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
    }
}
```

2. `AdbKeyProvider.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.impl.crypto

internal interface AdbKeyProvider {
    fun getOrCreateKey(alias: String): AdbKey
}

internal class DefaultAdbKeyProvider(
    private val keyStore: AdbKeyStore
) : AdbKeyProvider {
    private val cache = mutableMapOf<String, AdbKey>()

    @Synchronized
    override fun getOrCreateKey(alias: String): AdbKey {
        return cache.getOrPut(alias) { AdbKey(keyStore, alias) }
    }
}
```

3. `AdbKey.kt`:
Mark as `internal class AdbKey(...) : AdbIdentity`.
Implement:
```kotlin
override fun encodedPublicKey(): ByteArray = adbPublicKey
override fun sign(token: ByteArray): ByteArray = signData(token)
override fun createSslContext(): SSLContext = sslContext
```
Add narrow suppression for `TrustAllX509TrustManager`:
```kotlin
// In ADB wireless pairing, authentication is verified via SPAKE2 key exchange over TLS, not CA certificates.
@SuppressLint("TrustAllX509TrustManager")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :impl:testDebugUnitTest --tests PreferenceAdbKeyStoreTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/crypto/ impl/src/test/java/io/github/khoanguyenbk18/adbpairing/impl/crypto/
git commit -m "feat(impl): implement alias-aware AdbKeyStore and AdbKey adhering to AdbIdentity"
```

---

### Task 4: Cancellation-Safe Network & Pairing Engine (`:impl`)

**Files:**
- Create: `impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/nsd/AdbNsdDiscovery.kt`
- Create: `impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/pairing/AdbPairingClient.kt`
- Create: `impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/pairing/AdbPairingEngine.kt`
- Delete: `impl/src/main/java/com/devindie/zola/adb/pairing/impl/nsd/*`
- Delete: `impl/src/main/java/com/devindie/zola/adb/pairing/impl/pairing/*`

**Interfaces:**
- Consumes: `AdbKey` from Task 3, `moe.shizuku.manager.adb.PairingContext`
- Produces: `internal interface AdbNsdDiscovery`, `internal class AndroidAdbNsdDiscovery`, `internal class AdbPairingClient`, `internal interface AdbPairingEngine`, `internal class DefaultAdbPairingEngine`

- [ ] **Step 1: Implement AndroidAdbNsdDiscovery**

Move to `io.github.khoanguyenbk18.adbpairing.impl.nsd.AndroidAdbNsdDiscovery`.
Remove legacy `AdbMdns.kt`.
In `AndroidAdbNsdDiscovery`:
- Implement `discover(serviceType: String, serviceNameFilter: String?): Flow<DiscoveredService>` using `callbackFlow`.
- Add local port check on `127.0.0.1`.
- Clean up listeners in `awaitClose`.

- [ ] **Step 2: Implement AdbPairingClient with socket timeouts and cancellation cleanup**

In `AdbPairingClient.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.impl.pairing

import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.impl.crypto.AdbKey
import moe.shizuku.manager.adb.PairingContext
import org.conscrypt.Conscrypt
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket

internal class AdbPairingClient(
    private val port: Int,
    private val pairCode: String,
    private val key: AdbKey,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
    private val logger: AdbLogger = AdbLogger { _, _, _, _ -> }
) : Closeable {
    private val host: String = "127.0.0.1"
    private var rawSocket: Socket? = null
    private var sslSocket: SSLSocket? = null
    // ...
    fun start(): Boolean {
        rawSocket = Socket().apply {
            tcpNoDelay = true
            soTimeout = readTimeoutMs
            connect(InetSocketAddress(host, port), connectTimeoutMs)
        }
        // ...
    }

    override fun close() {
        try { sslSocket?.close() } catch (_: Throwable) {}
        try { rawSocket?.close() } catch (_: Throwable) {}
        // destroy pairing context
    }
}
```

- [ ] **Step 3: Implement DefaultAdbPairingEngine**

```kotlin
package io.github.khoanguyenbk18.adbpairing.impl.pairing

import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.impl.crypto.AdbKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface AdbPairingEngine {
    suspend fun pair(port: Int, code: String, key: AdbKey, timeoutMs: Long): Boolean
}

internal class DefaultAdbPairingEngine(
    private val logger: AdbLogger
) : AdbPairingEngine {
    override suspend fun pair(port: Int, code: String, key: AdbKey, timeoutMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            AdbPairingClient(
                port = port,
                pairCode = code,
                key = key,
                connectTimeoutMs = timeoutMs.coerceAtMost(10_000L).toInt(),
                readTimeoutMs = timeoutMs.coerceAtMost(10_000L).toInt(),
                logger = logger
            ).use { it.start() }
        }
    }
}
```

- [ ] **Step 4: Verify compilation of pairing engine**

Run: `./gradlew :impl:compileDebugKotlin`
Expected: May report missing AdbPairingManagerImpl (addressed in Task 5).

- [ ] **Step 5: Commit**

```bash
git add impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/nsd/ impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/pairing/
git commit -m "feat(impl): implement cancellation-safe discovery and socket timeouts in pairing engine"
```

---

### Task 5: Orchestrator, Factory Entry Point & Redacted Logging (`:impl`)

**Files:**
- Create: `impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/AdbPairingOptions.kt`
- Create: `impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/AdbPairing.kt`
- Create: `impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/AdbPairingManagerImpl.kt`
- Delete: `impl/src/main/java/com/devindie/zola/adb/pairing/impl/AdbPairingFactory.kt`
- Delete: `impl/src/main/java/com/devindie/zola/adb/pairing/impl/AdbPairingManagerImpl.kt`
- Delete: `impl/src/main/java/com/devindie/zola/adb/pairing/impl/*`

**Interfaces:**
- Consumes: API contracts from Task 2, `AdbKey` from Task 3, `AdbPairingEngine` & `AdbNsdDiscovery` from Task 4
- Produces: Public entry point `AdbPairing`, options `AdbPairingOptions`, orchestrator `AdbPairingManagerImpl`

- [ ] **Step 1: Define AdbPairingOptions and AdbPairing entry point**

1. `AdbPairingOptions.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.impl

import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.api.NoOpAdbLogger

data class AdbPairingOptions(
    val identityAlias: String = "adbkey",
    val logger: AdbLogger = NoOpAdbLogger,
    val discoveryTimeoutMs: Long = 60_000L,
    val pairingTimeoutMs: Long = 30_000L,
    val connectDiscoveryTimeoutMs: Long = 30_000L
)
```

2. `AdbPairing.kt`:
```kotlin
package io.github.khoanguyenbk18.adbpairing.impl

import android.content.Context
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingManager

object AdbPairing {
    fun create(
        context: Context,
        options: AdbPairingOptions = AdbPairingOptions()
    ): AdbPairingManager {
        return AdbPairingManagerImpl(context.applicationContext, options)
    }
}
```

- [ ] **Step 2: Implement AdbPairingManagerImpl**

In `AdbPairingManagerImpl.kt`:
- Mark as `internal class AdbPairingManagerImpl`.
- Implement `getIdentity(): AdbIdentity = keyProvider.getOrCreateKey(options.identityAlias)`.
- In `pair()`:
  - Route all logs through `options.logger` (no direct `android.util.Log` calls).
  - Never log raw request or secrets.
  - Re-throw `CancellationException` in all catch blocks:
    ```kotlin
    catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emit(AdbPairingEvent.Failed(...))
    }
    ```
- Implement `discoverPairingEndpoints(options)` and `discoverConnectionEndpoints()`.
- Delete remaining files in old package `com/devindie`.

- [ ] **Step 3: Compile implementation module**

Run: `./gradlew :impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add impl/src/main/java/io/github/khoanguyenbk18/adbpairing/impl/
git commit -m "feat(impl): implement AdbPairing factory and internal AdbPairingManagerImpl"
```

---

### Task 6: JNI Consumer Rules & Release Lint

**Files:**
- Modify: `impl/consumer-rules.pro`
- Modify: `impl/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `moe.shizuku.manager.adb.PairingContext`
- Produces: Precise R8 / ProGuard consumer rules, verified clean release lint

- [ ] **Step 1: Configure `impl/consumer-rules.pro`**

```proguard
# Preserve JNI methods and fields accessed from libadb.so
-keep class moe.shizuku.manager.adb.PairingContext {
    private <fields>;
    private <methods>;
    public <methods>;
}
```

- [ ] **Step 2: Update AndroidManifest.xml namespace**

Ensure package in `impl/src/main/AndroidManifest.xml` is aligned with `io.github.khoanguyenbk18.adbpairing.impl`.

- [ ] **Step 3: Run release lint check**

Run: `./gradlew :impl:lintRelease`
Expected: 0 errors.

- [ ] **Step 4: Commit**

```bash
git add impl/consumer-rules.pro impl/src/main/AndroidManifest.xml
git commit -m "fix(impl): update consumer proguard rules and verify release lint"
```

---

### Task 7: Comprehensive Unit Tests & Verification

**Files:**
- Create: `impl/src/test/java/io/github/khoanguyenbk18/adbpairing/impl/AdbPairingManagerImplTest.kt`
- Delete: `impl/src/test/java/com/devindie/zola/adb/pairing/impl/AdbPairingManagerImplTest.kt`
- Modify: `README.md`
- Modify: `REFACTORING.md`

**Interfaces:**
- Consumes: Complete API and implementation modules
- Produces: Full automated test suite verifying all 10 requirements and documentation update

- [ ] **Step 1: Write comprehensive tests in `AdbPairingManagerImplTest.kt`**

Test scenarios:
1. `testSuccessfulQrPairing`: full event pipeline ending in `AdbPairingEvent.Success`.
2. `testSuccessfulManualPairingWithPort`: skips discovery and completes handshake.
3. `testCancellationProducesNoFailureEvent`: cancelling flow produces no `Failed` event.
4. `testSecretsNeverLogged`: fake `AdbLogger` inspects all logged messages and verifies pairing code / QR password never appear.
5. `testTimeoutFailures`: validates timeout events across all 3 phases.
6. `testDiscoverPairingEndpoints`: flow emits discovered services and lost services.
7. `testDiscoverConnectionEndpoints`: flow emits connection endpoints.

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew :api:test :impl:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (all tests pass).

- [ ] **Step 3: Build release AAR**

Run: `./gradlew :impl:assembleRelease`
Expected: BUILD SUCCESSFUL, producing `impl/build/outputs/aar/impl-release.aar`.

- [ ] **Step 4: Update README.md and REFACTORING.md**

Update code snippets in `README.md` to show:
```kotlin
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairing

val manager = AdbPairing.create(context)
```
Update `REFACTORING.md` noting resolved items.

- [ ] **Step 5: Run complete verification suite**

Run: `./gradlew :api:test :impl:testDebugUnitTest :impl:lintRelease :impl:assembleRelease`
Expected: BUILD SUCCESSFUL across all tasks.

- [ ] **Step 6: Commit**

```bash
git add impl/src/test/ README.md REFACTORING.md
git commit -m "test: add comprehensive test suite and update docs for v0.1.0"
```
