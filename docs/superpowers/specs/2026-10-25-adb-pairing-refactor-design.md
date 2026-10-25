# ADB Pairing Architecture Design Spec

**Date**: 2026-10-25  
**Topic**: Library Refactoring & Boundary Tightening for v0.1.0  
**Target Group**: `io.github.khoanguyenbk18.adbpairing`

---

## 1. Overview & Objectives

The `adb-pairing` library provides Android wireless ADB pairing and TLS connection endpoint discovery. This refactoring tightens the architecture for a clean `0.1.0` release:

1. **Enforce Real Module Boundaries**: Introduce public `AdbIdentity` and discovery contracts in `:api` so consumers never import internal classes from `:impl`.
2. **Package & Identity Neutralization**: Rename packages from `com.devindie.zola.adb.pairing` to `io.github.khoanguyenbk18.adbpairing`. Keep `moe.shizuku.manager.adb.PairingContext` intact for JNI compatibility with `libadb.so`.
3. **Internalize Implementation**: Make all `:impl` classes `internal` except for the single factory entry point `AdbPairing`.
4. **Secret Redaction & Pluggable Logging**: Strip credentials from `toString()` representations and replace direct Android logging with a pluggable `AdbLogger` defaulting to no-op.
5. **Robust Cancellation & Timeouts**: Re-throw `CancellationException` in all flows, configure socket connection/read timeouts, and close resources deterministically on coroutine cancellation.
6. **Alias-Aware Key Storage**: Refactor `AdbKeyStore` to support distinct key aliases and move identity configuration to factory options.
7. **Raise `minSdk` to 30**: Simplify platform checks, eliminate API-level violations, and narrowly suppress TLS TrustManager warnings with explicit SPAKE2 security context.
8. **JNI Consumer ProGuard Rules**: Embed targeted ProGuard consumer rules preserving `PairingContext` JNI bindings.
9. **Remove Obsolete Dependencies**: Remove `androidx.lifecycle:lifecycle-livedata-core` and delete legacy `AdbMdns.kt`.
10. **Build System Compatibility**: Revert `gradle/wrapper/gradle-wrapper.properties` to Gradle 8.13 to maintain compatibility with AGP 8.12.0.

---

## 2. Module Specifications

### 2.1 API Module (`:api`)
* **Package**: `io.github.khoanguyenbk18.adbpairing.api`
* **Dependencies**: `api(libs.kotlinx.coroutines.core)` (pure Kotlin/JVM, no Android dependencies).

#### Core Contracts

```kotlin
package io.github.khoanguyenbk18.adbpairing.api

import kotlinx.coroutines.flow.Flow
import javax.net.ssl.SSLContext

/**
 * Public cryptographic identity representing this client in ADB TLS and authentication exchanges.
 */
interface AdbIdentity {
    val name: String
    fun encodedPublicKey(): ByteArray
    fun sign(token: ByteArray): ByteArray
    fun createSslContext(): SSLContext
}

/**
 * Public interface for ADB wireless pairing and discovery operations.
 */
interface AdbPairingManager {
    /** Retrieves or generates the local ADB cryptographic identity. */
    suspend fun getIdentity(): AdbIdentity

    /** Initiates the pairing flow according to the given [request]. */
    fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent>

    /** Discovers endpoints advertising the ADB pairing service (_adb-tls-pairing._tcp). */
    fun discoverPairingEndpoints(
        options: AdbDiscoveryOptions = AdbDiscoveryOptions()
    ): Flow<AdbDiscoveryEvent>

    /** Discovers endpoints advertising the ADB TLS connect service (_adb-tls-connect._tcp). */
    fun discoverConnectionEndpoints(): Flow<AdbConnectionEndpoint>
}
```

#### Pluggable Logger

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

#### Discovery Events & Options

```kotlin
package io.github.khoanguyenbk18.adbpairing.api

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

#### Request Validation & Secret Masking

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

`QrPairingSession` also overrides `toString()`:
```kotlin
data class QrPairingSession(
    val sessionId: String,
    val serviceName: String,
    val password: String
) {
    override fun toString(): String = "QrPairingSession(sessionId=$sessionId, serviceName=$serviceName, password=******)"
    fun qrContent(): String = "WIFI:T:ADB;S:$serviceName;P:$password;;"
}
```

---

### 2.2 Implementation Module (`:impl`)
* **Package**: `io.github.khoanguyenbk18.adbpairing.impl`
* **JNI Package**: `moe.shizuku.manager.adb` (retained for binary compatibility)
* **`minSdk`**: 30

#### Public Entry Point

```kotlin
package io.github.khoanguyenbk18.adbpairing.impl

import android.content.Context
import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingManager
import io.github.khoanguyenbk18.adbpairing.api.NoOpAdbLogger

data class AdbPairingOptions(
    val identityAlias: String = "adbkey",
    val logger: AdbLogger = NoOpAdbLogger,
    val discoveryTimeoutMs: Long = 60_000L,
    val pairingTimeoutMs: Long = 30_000L,
    val connectDiscoveryTimeoutMs: Long = 30_000L
)

object AdbPairing {
    fun create(
        context: Context,
        options: AdbPairingOptions = AdbPairingOptions()
    ): AdbPairingManager {
        return AdbPairingManagerImpl(context.applicationContext, options)
    }
}
```

#### Internal Classes & Visibility

| Class | Visibility | Responsibility |
|---|---|---|
| `AdbPairingManagerImpl` | `internal` | Implements `AdbPairingManager`, orchestrates flows and cancellation. |
| `AdbKey` | `internal` | Implements `AdbIdentity`, handles RSA key generation, signing, and TLS Context. |
| `AdbKeyStore` | `internal` | Interface for alias-aware persistence: `load(alias)`, `save(alias, bytes)`. |
| `PreferenceAdbKeyStore` | `internal` | SharedPreferences implementation keyed by `"adbkey_$alias"`. |
| `AdbKeyProvider` | `internal` | Provides/caches `AdbKey` instances. |
| `AndroidAdbNsdDiscovery`| `internal` | Android NsdManager callback flow wrapper. |
| `AdbPairingClient` | `internal` | Low-level TLS socket and SPAKE2 packet exchange. |
| `DefaultAdbPairingEngine`| `internal` | Coroutine wrapper around `AdbPairingClient`. |
| `moe.shizuku.manager.adb.PairingContext` | `internal` | JNI bridge to `libadb.so`. |

#### Cancellation & Resource Management
1. In `AdbPairingManagerImpl`, all `try/catch` blocks catch and immediately re-throw `kotlinx.coroutines.CancellationException`.
2. `AdbPairingClient` sets:
   - `socket.connect(InetSocketAddress(host, port), connectTimeoutMs.toInt())`
   - `socket.soTimeout = readTimeoutMs.toInt()`
3. In `AdbPairingClient.close()`, streams and socket are closed cleanly so any thread blocked on reading is promptly interrupted.

#### ProGuard Rules (`impl/consumer-rules.pro`)
```proguard
# Preserve PairingContext JNI bridge
-keep class moe.shizuku.manager.adb.PairingContext {
    private <fields>;
    private <methods>;
    public <methods>;
}
```

---

## 3. Verification Plan

1. **Unit Tests in `:api`**:
   - `AdbPairingApiTest.kt`: Tests input validation for code, ports, QR inputs, and secret redaction in `toString()`.
2. **Unit Tests in `:impl`**:
   - `AdbPairingManagerImplTest.kt`:
     - Cancellation produces NO failure event.
     - Sockets and listeners close when flow collection is cancelled.
     - Logs received by `AdbLogger` never contain sensitive codes or passwords.
     - `PreferenceAdbKeyStore` maintains distinct keys for different aliases.
     - `discoverPairingEndpoints` and `discoverConnectionEndpoints` emit correct event streams.
     - SPAKE2 handshake failure and timeout phases emit correct typed events.
3. **Build & Lint Verification**:
   - `./gradlew :api:test`
   - `./gradlew :impl:testDebugUnitTest`
   - `./gradlew :impl:lintRelease` (0 errors, 0 warnings or only suppressed trust manager)
   - `./gradlew :impl:assembleRelease`
