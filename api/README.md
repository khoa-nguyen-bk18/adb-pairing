# ADB Pairing API (`:api`)

`adb-pairing:api` is the platform-neutral contract for Android wireless ADB pairing. It contains the requests, progress events, typed failures, discovery events, cryptographic identity abstraction, logger contract, and QR-session model used by pairing clients.

The module is a pure Kotlin/JVM library with zero Android, DI, or UI dependencies.

## Dependency

```kotlin
// settings.gradle.kts
maven("https://jitpack.io")

// build.gradle.kts
dependencies {
    implementation("com.github.khoa-nguyen-bk18.adb-pairing:api:0.1.0")
}
```

## Public Contract

```kotlin
interface AdbPairingManager {
    suspend fun getIdentity(): AdbIdentity
    fun getIdentitySync(): AdbIdentity

    fun pair(request: AdbPairingRequest): Flow<AdbPairingEvent>
    fun pair(pairingCode: String, port: Int? = null): Flow<AdbPairingEvent>
    fun pair(session: QrPairingSession): Flow<AdbPairingEvent>

    fun discoverPairingEndpoints(
        options: AdbDiscoveryOptions = AdbDiscoveryOptions()
    ): Flow<AdbDiscoveryEvent>

    fun discoverConnectionEndpoints(): Flow<AdbConnectionEndpoint>
}

// One-call suspend extensions returning Result<AdbConnectionEndpoint>:
suspend fun AdbPairingManager.awaitPairing(
    pairingCode: String,
    port: Int? = null,
    onProgress: (AdbPairingEvent) -> Unit = {}
): Result<AdbConnectionEndpoint>

suspend fun AdbPairingManager.awaitPairing(
    session: QrPairingSession,
    onProgress: (AdbPairingEvent) -> Unit = {}
): Result<AdbConnectionEndpoint>
```

## Manual Pairing

```kotlin
pairingManager.awaitPairing(pairingCode = "123456")
    .onSuccess { endpoint -> connect(endpoint) }
    .onFailure { error -> showFailure(error) }
```

## QR Pairing

```kotlin
val session = QrPairingSession.generate()
val qrText = session.qrContent() // "WIFI:T:ADB;S:<serviceName>;P:<password>;;"

pairingManager.awaitPairing(session)
    .onSuccess { endpoint -> connect(endpoint) }
```

## Cryptographic Identity

```kotlin
val identity = pairingManager.getIdentity()
val publicKey = identity.encodedPublicKey()
val sslContext = identity.createSslContext()
val signature = identity.sign(tokenBytes)
```
