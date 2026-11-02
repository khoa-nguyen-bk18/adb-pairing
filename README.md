# ADB Pairing for Android

[![CI](https://github.com/khoa-nguyen-bk18/adb-pairing/actions/workflows/ci.yml/badge.svg)](https://github.com/khoa-nguyen-bk18/adb-pairing/actions/workflows/ci.yml)
[![API 30+](https://img.shields.io/badge/API-30%2B-brightgreen.svg?style=flat)](https://developer.android.com/about/versions/11)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg?logo=kotlin)](https://kotlinlang.org)

A lightweight, coroutine-first Android library for **on-device Wireless ADB Pairing** (Android 11 / API 30+).

Encapsulates mDNS discovery (`_adb-tls-pairing._tcp` & `_adb-tls-connect._tcp`), hardware-backed RSA key generation/storage, TLS 1.3 + SPAKE2 native handshake (`libadb.so`), and endpoint resolution with **minimal setup**.

---

## 1. Installation (Zero Credentials, Zero Manifest Boilerplate)

> `INTERNET` permission and R8/ProGuard consumer rules (`consumer-rules.pro`) are **merged automatically** from the library AAR. You do not need to edit `AndroidManifest.xml` or `proguard-rules.pro`.

### Step 1: Add Repository (`settings.gradle.kts`)

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

### Step 2: Add Dependency (`app/build.gradle.kts`)

```kotlin
dependencies {
    implementation("com.github.khoa-nguyen-bk18.adb-pairing:impl:0.1.0")
}
```

<details>
<summary>Alternative: Multi-module `:api` dependency, Git Submodule, or GitHub Packages</summary>

#### Pure Kotlin / Domain Modules (`:api` only)
If you have domain or presentation modules that should remain pure Kotlin/JVM without Android dependencies:
```kotlin
dependencies {
    implementation("com.github.khoa-nguyen-bk18.adb-pairing:api:0.1.0")
}
```

#### Local Composite Build (`includeBuild`)
```kotlin
// settings.gradle.kts
includeBuild("external/adb-pairing")
```

#### GitHub Packages (`maven.pkg.github.com`)
> Note: GitHub Packages' Maven registry requires a GitHub token with `read:packages` even for public repositories (a GitHub platform limitation). Use JitPack above if you want zero-credential setup.
```kotlin
maven("https://maven.pkg.github.com/khoa-nguyen-bk18/adb-pairing") {
    credentials {
        username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
        password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
    }
}
// Artifact: io.github.khoanguyenbk18.adbpairing:impl:0.1.0
```
</details>

---

## 2. Quick Start (3 Lines of Code)

### Option A: 6-Digit Manual Pairing Code

```kotlin
import io.github.khoanguyenbk18.adbpairing.api.awaitPairing
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairing

val pairingManager = AdbPairing.getInstance(context)

// Auto-discovers the pairing port via mDNS, performs TLS + SPAKE2 handshake,
// and resolves the final ADB connection endpoint (host + port):
pairingManager.awaitPairing(pairingCode = "123456")
    .onSuccess { endpoint -> connectToAdb(endpoint.host, endpoint.port) }
    .onFailure { error -> showError(error.message) }
```

*(If you already know the pairing port, pass `awaitPairing(pairingCode = "123456", port = 37123)` to skip mDNS discovery).*

### Option B: QR Code Pairing

```kotlin
import io.github.khoanguyenbk18.adbpairing.api.QrPairingSession
import io.github.khoanguyenbk18.adbpairing.api.awaitPairing
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairing

val session = QrPairingSession.generate()
renderQrBitmap(session.qrContent()) // "WIFI:T:ADB;S:studio-XXXXXXXXXX;P:XXXXXXXXXXXXXXXXXXXX;;"

AdbPairing.getInstance(context).awaitPairing(session)
    .onSuccess { endpoint -> connectToAdb(endpoint.host, endpoint.port) }
    .onFailure { error -> showError(error.message) }
```

---

## 3. Advanced Usage & Reactive Flows

### Listening to Step-by-Step Pairing Progress (`Flow<AdbPairingEvent>`)

If your UI needs granular progress updates (e.g. showing when the pairing dialog is detected vs. when keys are being exchanged), collect `pair(...)` directly. Cancelling the coroutine scope automatically aborts sockets and unregisters mDNS listeners:

```kotlin
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingEvent
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairing

AdbPairing.getInstance(context).pair(pairingCode = "123456").collect { event ->
    when (event) {
        AdbPairingEvent.SearchingForPairingService -> showStatus("Waiting for pairing dialog...")
        is AdbPairingEvent.PairingServiceFound -> showStatus("Found port ${event.port}...")
        AdbPairingEvent.PairingInProgress -> showStatus("Exchanging keys...")
        AdbPairingEvent.SearchingForConnectionService -> showStatus("Resolving ADB connect port...")
        is AdbPairingEvent.Success -> connectToAdb(event.endpoint)
        is AdbPairingEvent.Failed -> handleFailure(event.failure) // Typed AdbPairingFailure
    }
}
```

### Connecting to ADB After Pairing (And on Subsequent App Launches)

Pairing only needs to be performed **once**. On subsequent app launches, resolve the current wireless ADB port via `discoverConnectionEndpoints()` and authenticate using `getIdentity()`:

```kotlin
import kotlinx.coroutines.flow.first

val pairingManager = AdbPairing.getInstance(context)

// 1. Find the active Wireless Debugging connect port (_adb-tls-connect._tcp)
val endpoint = pairingManager.discoverConnectionEndpoints().first()

// 2. Use the persisted RSA identity for TLS (A_STLS) or classic AUTH signature
val identity = pairingManager.getIdentity()
val sslContext = identity.createSslContext()
val publicKeyBytes = identity.encodedPublicKey()
```

### Pre-discovering the Pairing Dialog (For Notification `RemoteInput` Workflows)

> **Android UX Tip**: Android closes the *Wireless Debugging* pairing code dialog if the user leaves Settings. To prompt the user with a Notification while they stay inside Developer Options, observe `discoverPairingEndpoints()`:

```kotlin
import io.github.khoanguyenbk18.adbpairing.api.AdbDiscoveryEvent

AdbPairing.getInstance(context).discoverPairingEndpoints().collect { event ->
    when (event) {
        is AdbDiscoveryEvent.Discovered -> showCodeInputNotification(port = event.endpoint.port)
        is AdbDiscoveryEvent.Lost -> dismissCodeInputNotification()
        is AdbDiscoveryEvent.Failed -> Unit
    }
}
```

---

## 4. Custom Configuration (`AdbPairingOptions`)

When you need custom timeouts, a custom keystore alias, diagnostic logging, or legacy key migration, use `AdbPairing.create(context, options)`:

```kotlin
import android.util.Log
import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairing
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairingOptions

val pairingManager = AdbPairing.create(
    context = context,
    options = AdbPairingOptions(
        identityAlias = "adbkey",
        discoveryTimeoutMs = 60_000L,
        pairingTimeoutMs = 30_000L,
        connectDiscoveryTimeoutMs = 30_000L,
        logger = AdbLogger { priority, tag, msg, tr -> Log.println(priority, tag, msg) },
        legacyKeyBytesProvider = { alias -> null } // Optional PKCS#8 migration
    )
)
```

---

## Module Documentation & Local Build

- [`api/README.md`](api/README.md) — Pure Kotlin API contracts, events, and typed failure hierarchy.
- [`impl/README.md`](impl/README.md) — Android implementation details and native binaries.

```shell
./gradlew :api:test :impl:testDebugUnitTest :impl:lintRelease :impl:assembleRelease
```
