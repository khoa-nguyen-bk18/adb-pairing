package io.github.khoanguyenbk18.adbpairing.impl.pairing

import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.impl.crypto.AdbKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface AdbPairingEngine {
    suspend fun pair(port: Int, code: String, key: AdbKey, timeoutMs: Long): Boolean
}

internal class DefaultAdbPairingEngine(
    private val logger: AdbLogger = AdbLogger { _, _, _, _ -> }
) : AdbPairingEngine {
    override suspend fun pair(port: Int, code: String, key: AdbKey, timeoutMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            AdbPairingClient(
                port = port,
                pairCode = code,
                key = key,
                connectTimeoutMs = timeoutMs.coerceAtMost(10_000L).toInt(),
                readTimeoutMs = timeoutMs.coerceAtMost(10_000L).toInt(),
                logger = logger
            ).use { it.start() }
        }
    }
}
