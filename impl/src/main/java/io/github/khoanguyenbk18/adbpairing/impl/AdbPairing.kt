package io.github.khoanguyenbk18.adbpairing.impl

import android.content.Context
import io.github.khoanguyenbk18.adbpairing.api.AdbPairingManager

/**
 * Public entry point for creating [AdbPairingManager] instances.
 */
object AdbPairing {
    fun create(
        context: Context,
        options: AdbPairingOptions = AdbPairingOptions()
    ): AdbPairingManager {
        return AdbPairingManagerImpl(context.applicationContext, options)
    }
}
