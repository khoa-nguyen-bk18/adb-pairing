package io.github.khoanguyenbk18.adbpairing.impl.crypto

internal interface AdbKeyProvider {
    fun getOrCreateKey(alias: String): AdbKey
}

internal class DefaultAdbKeyProvider(
    private val keyStore: AdbKeyStore
) : AdbKeyProvider {
    private val cache = mutableMapOf<String, AdbKey>()

    @Synchronized
    override fun getOrCreateKey(alias: String): AdbKey {
        return cache.getOrPut(alias) { AdbKey(keyStore, alias) }
    }
}
