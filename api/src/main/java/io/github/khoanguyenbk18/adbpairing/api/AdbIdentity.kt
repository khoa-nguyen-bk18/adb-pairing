package io.github.khoanguyenbk18.adbpairing.api

import javax.net.ssl.SSLContext

/**
 * Public cryptographic identity representing this client in ADB TLS and authentication exchanges.
 */
interface AdbIdentity {
    val name: String
    fun encodedPublicKey(): ByteArray
    fun sign(token: ByteArray): ByteArray
    fun createSslContext(): SSLContext
}
