package io.github.khoanguyenbk18.adbpairing.impl.crypto

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Base64

internal interface AdbKeyStore {
    fun load(alias: String): ByteArray?
    fun save(alias: String, bytes: ByteArray)
}

internal class PreferenceAdbKeyStore(
    private val preferences: SharedPreferences
) : AdbKeyStore {
    override fun load(alias: String): ByteArray? {
        val prefKey = "adbkey_$alias"
        if (!preferences.contains(prefKey)) return null
        val base64 = preferences.getString(prefKey, null) ?: return null
        return Base64.getDecoder().decode(base64)
    }

    override fun save(alias: String, bytes: ByteArray) {
        val prefKey = "adbkey_$alias"
        preferences.edit {
            putString(prefKey, Base64.getEncoder().encodeToString(bytes))
        }
    }
}
