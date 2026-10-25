# ADB Pairing Implementation

`adb-pairing:impl` is the Android implementation of the contracts in `adb-pairing:api`. It hides Android NSD discovery, ADB key creation and persistence, TLS/SPAKE2 pairing, sockets, and the native `libadb.so` binaries.

Applications should expose `AdbPairingManager` to their features and keep direct use of this module in the application composition root.

## Requirements

- Android library with `minSdk 28`.
- Wireless ADB pairing requires Android 11 / API 30 or newer. Older versions receive `AdbPairingFailure.UnsupportedPlatformVersion`.
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

Use the factory at the application boundary:

```kotlin
val pairingManager: AdbPairingManager =
    AdbPairingFactory.create(applicationContext)
```

The default factory stores ADB key material in the implementation's private `adb_pairing` preferences file.

If another ADB component must use the same identity after pairing, inject a shared `SharedPreferences` instance:

```kotlin
val preferences = applicationContext.getSharedPreferences(
    "shared_adb_credentials",
    Context.MODE_PRIVATE
)

val pairingManager = AdbPairingFactory.create(
    context = applicationContext,
    preferences = preferences
)
```

This is important when the application has a separate ADB connection client: pairing and later authentication must read the same persisted key.

## Dependency injection example

The implementation does not require a DI framework. With Hilt, bind it at the application level and expose only the API interface:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AdbPairingModule {
    @Provides
    @Singleton
    fun provideAdbPairingManager(
        @ApplicationContext context: Context
    ): AdbPairingManager = AdbPairingFactory.create(context)
}
```

Features can then depend on `AdbPairingManager` without importing implementation classes.

## Pairing

Pairing is driven through the API module:

```kotlin
val request = AdbPairingRequest.Manual(
    pairingCode = "123456",
    port = null // Discover the local pairing service through NSD.
)

pairingManager.pair(request).collect { event ->
    when (event) {
        is AdbPairingEvent.Success -> {
            val endpoint = event.result.connectionEndpoint
            // Hand endpoint to the application's ADB connection client.
        }
        is AdbPairingEvent.Failed -> handleFailure(event.failure)
        else -> updatePairingProgress(event)
    }
}
```

Collect the flow from a lifecycle-aware or explicitly owned coroutine scope. Cancelling that scope cancels in-flight discovery and pairing work.

## Portability notes

To reuse the feature in another project, copy or publish both `api` and `impl`, add their Gradle dependencies, and construct the manager at that project's composition root. UI, services, notifications, and the post-pair ADB connection lifecycle remain application responsibilities.

Run the module tests with:

```shell
./gradlew :api:test :impl:testDebugUnitTest
```
