# ADB Pairing Implementation (`:impl`)

`adb-pairing:impl` is the Android implementation of `adb-pairing:api`. It encapsulates Android NSD mDNS discovery, hardware-backed ADB key generation and persistence, TLS 1.3 / SPAKE2 pairing, sockets, and the native `libadb.so` binaries.

## Zero-Config Integration

- **`minSdk = 30`** (Android 11+ required for Wireless Debugging TLS pairing).
- **`android.permission.INTERNET`** is declared in the library's `AndroidManifest.xml` and merged automatically.
- **R8 / ProGuard rules** (`consumer-rules.pro`) are bundled automatically in the AAR.
- **Native binaries** are included for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- Transitively exports `adb-pairing:api`.

```kotlin
// settings.gradle.kts
maven("https://jitpack.io")

// app/build.gradle.kts
dependencies {
    implementation("com.github.khoa-nguyen-bk18.adb-pairing:impl:0.1.0")
}
```

## Usage

### Default Singleton (1 Line)

```kotlin
import io.github.khoanguyenbk18.adbpairing.api.awaitPairing
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairing

AdbPairing.getInstance(context).awaitPairing(pairingCode = "123456")
    .onSuccess { endpoint -> /* Connect to endpoint.host:endpoint.port */ }
```

### Custom Instance (`AdbPairingOptions`)

```kotlin
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairing
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairingOptions

val pairingManager = AdbPairing.create(
    context = applicationContext,
    options = AdbPairingOptions(
        identityAlias = "my-app-adb",
        discoveryTimeoutMs = 45_000L,
        pairingTimeoutMs = 30_000L
    )
)
```
