# ADB Pairing Implementation

`adb-pairing:impl` is the Android implementation of the contracts in `adb-pairing:api`. It encapsulates Android NSD discovery, ADB key generation and persistence, TLS/SPAKE2 pairing, sockets, and the native `libadb.so` binaries.

All implementation classes are marked `internal`. Applications interact only with the `AdbPairing` composition entry point and `AdbPairingManager`.

## Requirements

- Android library with `minSdk 30`.
- Wireless ADB pairing requires Android 11 / API 30 or newer.
- The consuming application must declare `android.permission.INTERNET` in its manifest.
- Native binaries are packaged for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- The implementation depends on `adb-pairing:api` and exposes its public types transitively.

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Add the module

```kotlin
// settings.gradle.kts
include(":api")
include(":impl")
```

```kotlin
// Application build.gradle.kts
dependencies {
    implementation(project(":api"))
    implementation(project(":impl"))
}
```

## Create the manager

Use the `AdbPairing` entry point at the application boundary:

```kotlin
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairing
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairingOptions

val pairingManager = AdbPairing.create(
    context = applicationContext,
    options = AdbPairingOptions(
        identityAlias = "my-app-adb",
        discoveryTimeoutMs = 45_000,
        pairingTimeoutMs = 30_000
    )
)
```

## Pairing

Pairing is driven through the API module contracts:

```kotlin
val request = AdbPairingRequest.Manual(
    pairingCode = "123456",
    port = null // Discover the local pairing service through NSD.
)

pairingManager.pair(request).collect { event ->
    when (event) {
        is AdbPairingEvent.Success -> {
            val endpoint = event.endpoint
            // Hand endpoint to the application's ADB connection client.
        }
        is AdbPairingEvent.Failed -> handleFailure(event.failure)
        else -> updatePairingProgress(event)
    }
}
```

Collect the flow from a coroutine scope. Cancelling that scope cleanly closes active sockets, unregisters discovery listeners, and produces no failure events.
