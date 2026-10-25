package com.devindie.zola.adb.pairing.impl.pairing

import android.annotation.SuppressLint
import com.devindie.zola.adb.pairing.impl.crypto.AdbKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface AdbPairingEngine {
    suspend fun pair(host: String, port: Int, code: String, key: AdbKey): Boolean
}

internal class DefaultAdbPairingEngine : AdbPairingEngine {
    // The public manager rejects API < 30 before this internal engine is invoked.
    @SuppressLint("NewApi")
    override suspend fun pair(host: String, port: Int, code: String, key: AdbKey): Boolean {
        return withContext(Dispatchers.IO) {
            AdbPairingClient(host, port, code, key).use { it.start() }
        }
    }
}
