# ADB Pairing API

`adb-pairing:api` is the platform-neutral contract for Android wireless ADB pairing. It contains the requests, progress events, typed failures, configuration, results, and QR-session model used by pairing clients.

The module is a Kotlin/JVM library. It has no dependency on Android, Hilt, Compose, or a particular application, so UI and domain code can depend on it without seeing pairing internals.

## Add the module

Include both pairing projects in the consuming build:

```kotlin
// settings.gradle.kts
include(":api")
include(":impl")
```

Code that only needs the public contract should depend on the API module:

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":api"))
}
```

## Public contract

The main entry point is `AdbPairingManager`:

```kotlin
interface AdbPairingManager {
    fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent>
    fun discoverPairingPort(config: AdbPairingConfig = AdbPairingConfig.DEFAULT): Flow<Int>
}
```

Both functions return cold flows. Work starts when the flow is collected, and cancelling collection cancels the active discovery or pairing operation.

## Manual pairing

If the port is already known, pass it with the six-digit pairing code:

```kotlin
val request = AdbPairingRequest.Manual(
    pairingCode = "123456",
    port = 37123
)

pairingManager.pair(request).collect { event ->
    when (event) {
        is AdbPairingEvent.Success -> connect(event.result.connectionEndpoint)
        is AdbPairingEvent.Failed -> showFailure(event.failure)
        else -> showProgress(event)
    }
}
```

When `port` is `null`, `pair()` discovers the pairing service first. A UI that asks for the port before accepting a code can instead collect `discoverPairingPort()` and then submit a manual request.

## QR pairing

Generate QR credentials and display the standard AOSP QR content:

```kotlin
val session = QrPairingSession.generate()
val qrText = session.qrContent()

val request = AdbPairingRequest.Qr(
    serviceName = session.serviceName,
    password = session.password
)

pairingManager.pair(request).collect(::handlePairingEvent)
```

The returned `AdbPairingResult` contains the TLS connection endpoint to use for subsequent ADB communication. Connecting to that endpoint is deliberately outside this API.

## Configuration and failures

Use `AdbPairingConfig` to customize the host, discovery and handshake timeouts, or persisted key alias:

```kotlin
val config = AdbPairingConfig(
    host = "127.0.0.1",
    discoveryTimeoutMs = 45_000,
    pairingTimeoutMs = 30_000,
    keyAlias = "my-app-adb-key"
)
```

Failures are exposed as `AdbPairingFailure`, including unsupported Android versions, invalid codes, discovery errors, connection errors, key-store errors, timeouts, and unexpected failures. Consumers should handle the typed failure instead of parsing exception messages.

## Testing consumers

Depend on `AdbPairingManager` in application code and provide a fake implementation in tests. This keeps Android NSD, cryptography, sockets, and native pairing code out of UI and domain tests.
