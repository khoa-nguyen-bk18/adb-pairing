# ADB Pairing API

`adb-pairing:api` is the platform-neutral contract for Android wireless ADB pairing. It contains the requests, progress events, typed failures, discovery events, identity abstraction, and QR-session model used by pairing clients.

The module is a pure Kotlin/JVM library. It has no dependency on Android, Hilt, Compose, or any UI framework, so UI and domain code can depend on it without seeing pairing internals.

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

The main interface is `AdbPairingManager`:

```kotlin
interface AdbPairingManager {
    suspend fun getIdentity(): AdbIdentity

    fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent>

    fun discoverPairingEndpoints(
        options: AdbDiscoveryOptions = AdbDiscoveryOptions()
    ): Flow<AdbDiscoveryEvent>

    fun discoverConnectionEndpoints(): Flow<AdbConnectionEndpoint>
}
```

All discovery and pairing functions return cold flows. Work starts when the flow is collected, and cancelling collection cancels the active discovery or pairing operation without emitting failure events.

## Manual pairing

If the port is already known, pass it with the six-digit pairing code:

```kotlin
val request = AdbPairingRequest.Manual(
    pairingCode = "123456",
    port = 37123
)

pairingManager.pair(request).collect { event ->
    when (event) {
        is AdbPairingEvent.Success -> connect(event.endpoint)
        is AdbPairingEvent.Failed -> showFailure(event.failure)
        else -> showProgress(event)
    }
}
```

When `port` is `null`, `pair()` discovers the pairing service first. Alternatively, a UI can collect `discoverPairingEndpoints()` to present discovered pairing ports before the user enters the code.

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

The returned `AdbPairingEvent.Success` contains the `AdbConnectionEndpoint` to use for subsequent ADB communication.

## Cryptographic identity

Consumers can obtain the local cryptographic credentials via `AdbIdentity`:

```kotlin
val identity = pairingManager.getIdentity()
val publicKey = identity.encodedPublicKey()
val sslContext = identity.createSslContext()
```

## Secret masking

`AdbPairingRequest` and `QrPairingSession` automatically mask sensitive pairing codes and passwords in their `toString()` outputs (`******`), ensuring credentials are never accidentally leaked into logs.
