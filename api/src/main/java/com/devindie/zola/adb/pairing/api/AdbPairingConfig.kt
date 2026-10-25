package com.devindie.zola.adb.pairing.api

/**
 * Configuration options for ADB wireless pairing.
 *
 * @property host Target host address (default: "127.0.0.1").
 * @property discoveryTimeoutMs Timeout for mDNS service discovery in milliseconds (default: 60 seconds).
 * @property pairingTimeoutMs Timeout for TLS handshake and pairing exchange in milliseconds (default: 30 seconds).
 * @property connectDiscoveryTimeoutMs Timeout for discovering the TLS connect service after pairing (default: 30 seconds).
 * @property keyAlias Key alias used for ADB key store persistence (default: "zola").
 */
data class AdbPairingConfig(
    val host: String = "127.0.0.1",
    val discoveryTimeoutMs: Long = 60_000L,
    val pairingTimeoutMs: Long = 30_000L,
    val connectDiscoveryTimeoutMs: Long = 30_000L,
    val keyAlias: String = "zola"
) {
    companion object {
        val DEFAULT = AdbPairingConfig()
    }
}
