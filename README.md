# ADB Pairing

Reusable Android wireless ADB pairing library, split into a platform-neutral API and an Android implementation.

> This is an initial private `0.x` extraction from Zola. The public surface may change while the implementation boundary is tightened. See [REFACTORING.md](REFACTORING.md).

## Artifacts

```text
io.github.khoanguyenbk18.adbpairing:api:0.1.0
io.github.khoanguyenbk18.adbpairing:impl:0.1.0
```

Most Android applications only need `impl`; it brings `api` transitively.

## Configure the private registry

Add credentials to the user-level `~/.gradle/gradle.properties` file:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_PAT_WITH_READ_PACKAGES
```

Then add the repository to the consuming project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/khoa-nguyen-bk18/adb-pairing")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
            content {
                includeGroup("io.github.khoanguyenbk18.adbpairing")
            }
        }
    }
}
```

Add the implementation artifact:

```kotlin
dependencies {
    implementation("io.github.khoanguyenbk18.adbpairing:impl:0.1.0")
}
```

The consuming application must declare:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Create the manager

```kotlin
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingManager
import io.github.khoanguyenbk18.adbpairing.impl.AdbPairing

val pairingManager: AdbPairingManager =
    AdbPairing.create(applicationContext)
```

See [api/README.md](api/README.md) and [impl/README.md](impl/README.md) for manual and QR pairing examples.

## Development

```shell
./gradlew :api:test :impl:testDebugUnitTest :impl:lintRelease :impl:assembleRelease
```

Publishing is performed by the release workflow when a GitHub release is published.
