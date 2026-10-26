package io.github.khoanguyenbk18.adbpairing.api

import java.security.PrivateKey
import java.security.cert.Certificate
import javax.net.ssl.SSLContext

/**
 * Public cryptographic identity representing this client in ADB TLS and authentication exchanges.
 */
interface AdbIdentity {
    val name: String
    val privateKey: PrivateKey
    val certificate: Certificate
    fun encodedPublicKey(): ByteArray
    fun sign(token: ByteArray): ByteArray
    fun createSslContext(): SSLContext
}
