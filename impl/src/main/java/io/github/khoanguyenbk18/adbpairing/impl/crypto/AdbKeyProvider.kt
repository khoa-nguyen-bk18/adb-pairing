package io.github.khoanguyenbk18.adbpairing.impl.crypto

internal interface AdbKeyProvider {
    fun getOrCreateKey(alias: String): AdbKey
}

internal class DefaultAdbKeyProvider(
    private val keyStore: AdbKeyStore,
    private val legacyKeyBytesProvider: ((String) -> ByteArray?)? = null
) : AdbKeyProvider {
    private val cache = mutableMapOf<String, AdbKey>()

    @Synchronized
    override fun getOrCreateKey(alias: String): AdbKey {
        return cache.getOrPut(alias) { AdbKey(keyStore, alias, legacyKeyBytesProvider) }
    }
}
