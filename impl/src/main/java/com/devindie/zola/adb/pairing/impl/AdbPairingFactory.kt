package com.devindie.zola.adb.pairing.impl

import android.content.Context
import android.content.SharedPreferences
import com.devindie.zola.adb.pairing.api.AdbPairingManager

object AdbPairingFactory {
    fun create(context: Context): AdbPairingManager {
        return AdbPairingManagerImpl(context.applicationContext)
    }

    fun create(
        context: Context,
        preferences: SharedPreferences
    ): AdbPairingManager {
        return AdbPairingManagerImpl(context.applicationContext, preferences)
    }
}
