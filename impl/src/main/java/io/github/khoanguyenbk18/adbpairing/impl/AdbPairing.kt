package io.github.khoanguyenbk18.adbpairing.impl

import android.content.Context
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingManager

/**
 * Public entry point for obtaining [AdbPairingManager] instances.
 */
object AdbPairing {
    @Volatile
    private var defaultInstance: AdbPairingManager? = null

    /**
     * Returns a shared singleton [AdbPairingManager] configured with default [AdbPairingOptions].
     */
    fun getInstance(context: Context): AdbPairingManager {
        return defaultInstance ?: synchronized(this) {
            defaultInstance ?: create(context.applicationContext).also { defaultInstance = it }
        }
    }

    /**
     * Creates a new [AdbPairingManager] instance with custom [options].
     */
    fun create(
        context: Context,
        options: AdbPairingOptions = AdbPairingOptions()
    ): AdbPairingManager {
        return AdbPairingManagerImpl(context.applicationContext, options)
    }
}
