package io.github.khoanguyenbk18.adbpairing.api

/**
 * Discovered endpoint where the ADB daemon is reachable.
 */
data class AdbConnectionEndpoint(
    val host: String = "127.0.0.1",
    val port: Int
)

/**
 * Configuration options for service discovery operations.
 */
data class AdbDiscoveryOptions(
    val timeoutMs: Long = 60_000L,
    val serviceNameFilter: String? = null
)

/**
 * Events emitted when discovering local ADB services.
 */
sealed interface AdbDiscoveryEvent {
    data class Discovered(val endpoint: AdbConnectionEndpoint, val serviceName: String) : AdbDiscoveryEvent
    data class Lost(val serviceName: String) : AdbDiscoveryEvent
    data class Failed(val reason: String?, val cause: Throwable? = null) : AdbDiscoveryEvent
}
