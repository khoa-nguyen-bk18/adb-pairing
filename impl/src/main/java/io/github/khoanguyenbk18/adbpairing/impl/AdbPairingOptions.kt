package io.github.khoanguyenbk18.adbpairing.impl

import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.api.NoOpAdbLogger

/**
 * Options for configuring the ADB pairing manager.
 *
 * @property identityAlias Key alias used for ADB key store persistence (default: "adbkey").
 * @property logger Pluggable logger interface (default: NoOpAdbLogger).
 * @property discoveryTimeoutMs Timeout for mDNS service discovery in milliseconds (default: 60s).
 * @property pairingTimeoutMs Timeout for TLS handshake and pairing exchange in milliseconds (default: 30s).
 * @property connectDiscoveryTimeoutMs Timeout for discovering TLS connect service after pairing (default: 30s).
 */
data class AdbPairingOptions(
    val identityAlias: String = "adbkey",
    val logger: AdbLogger = NoOpAdbLogger,
    val discoveryTimeoutMs: Long = 60_000L,
    val pairingTimeoutMs: Long = 30_000L,
    val connectDiscoveryTimeoutMs: Long = 30_000L
)
