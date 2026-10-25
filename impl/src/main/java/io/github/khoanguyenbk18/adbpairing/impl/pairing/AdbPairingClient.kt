package io.github.khoanguyenbk18.adbpairing.impl.pairing

import io.github.khoanguyenbk18.adbpairing.api.AdbLogger
import io.github.khoanguyenbk18.adbpairing.api.log
import io.github.khoanguyenbk18.adbpairing.impl.crypto.AdbKey
import moe.shizuku.manager.adb.PairingContext
import org.conscrypt.Conscrypt
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.Security
import javax.net.ssl.SSLSocket

private const val TAG = "AdbPairClient"

private const val kCurrentKeyHeaderVersion = 1.toByte()
private const val kMinSupportedKeyHeaderVersion = 1.toByte()
private const val kMaxSupportedKeyHeaderVersion = 1.toByte()
private const val kMaxPeerInfoSize = 8192
private const val kMaxPayloadSize = kMaxPeerInfoSize * 2

private const val kExportedKeyLabel = "adb-label\u0000"
private const val kExportedKeySize = 64

private const val kPairingPacketHeaderSize = 6

internal class AdbInvalidPairingCodeException : Exception("Adb invalid pairing code")

private class PeerInfo(
    val type: Byte, data: ByteArray
) {
    val data = ByteArray(kMaxPeerInfoSize - 1)

    init {
        data.copyInto(this.data, 0, 0, data.size.coerceAtMost(kMaxPeerInfoSize - 1))
    }

    enum class Type(val value: Byte) {
        ADB_RSA_PUB_KEY(0.toByte()), ADB_DEVICE_GUID(0.toByte()),
    }

    fun writeTo(buffer: ByteBuffer) {
        buffer.run {
            put(type)
            put(data)
        }
    }

    override fun toString(): String = "PeerInfo(type=$type, data=${data.contentToString()})"

    companion object {
        fun readFrom(buffer: ByteBuffer): PeerInfo {
            val type = buffer.get()
            val data = ByteArray(kMaxPeerInfoSize - 1)
            buffer.get(data)
            return PeerInfo(type, data)
        }
    }
}

private class PairingPacketHeader(
    val version: Byte, val type: Byte, val payload: Int
) {
    enum class Type(val value: Byte) {
        SPAKE2_MSG(0.toByte()), PEER_INFO(1.toByte())
    }

    fun writeTo(buffer: ByteBuffer) {
        buffer.run {
            put(version)
            put(type)
            putInt(payload)
        }
    }

    override fun toString(): String =
        "PairingPacketHeader(version=${version.toInt()}, type=${type.toInt()}, payload=$payload)"

    companion object {
        fun readFrom(buffer: ByteBuffer): PairingPacketHeader? {
            val version = buffer.get()
            val type = buffer.get()
            val payload = buffer.int

            if (version < kMinSupportedKeyHeaderVersion || version > kMaxSupportedKeyHeaderVersion) {
                return null
            }
            if (type != Type.SPAKE2_MSG.value && type != Type.PEER_INFO.value) {
                return null
            }
            if (payload <= 0 || payload > kMaxPayloadSize) {
                return null
            }

            return PairingPacketHeader(version, type, payload)
        }
    }
}

internal class AdbPairingClient(
    private val port: Int,
    private val pairCode: String,
    private val key: AdbKey,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
    private val logger: AdbLogger = AdbLogger { _, _, _, _ -> }
) : Closeable {

    private enum class State {
        Ready, ExchangingMsgs, ExchangingPeerInfo, Stopped
    }

    private val host: String = "127.0.0.1"
    private var rawSocket: Socket? = null
    private var sslSocket: SSLSocket? = null
    private var inputStream: DataInputStream? = null
    private var outputStream: DataOutputStream? = null

    private val peerInfo: PeerInfo = PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY.value, key.adbPublicKey)
    private var pairingContext: PairingContext? = null
    private var state: State = State.Ready

    fun start(): Boolean {
        setupTlsConnection()

        state = State.ExchangingMsgs

        if (!doExchangeMsgs()) {
            state = State.Stopped
            return false
        }

        state = State.ExchangingPeerInfo

        if (!doExchangePeerInfo()) {
            state = State.Stopped
            return false
        }

        state = State.Stopped
        return true
    }

    private fun setupTlsConnection() {
        if (!Security.getProviders().any { it.name == "Conscrypt" }) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        val sslContext = key.sslContext

        val socket = Socket().apply {
            tcpNoDelay = true
            soTimeout = readTimeoutMs
            connect(InetSocketAddress(host, port), connectTimeoutMs)
        }
        rawSocket = socket

        val ssl = sslContext.socketFactory
            .createSocket(socket, host, port, true) as SSLSocket
        sslSocket = ssl

        ssl.startHandshake()
        logger.log(AdbLogger.DEBUG, TAG, "Handshake succeeded. Cipher: ${ssl.session.cipherSuite}")

        inputStream = DataInputStream(ssl.inputStream)
        outputStream = DataOutputStream(ssl.outputStream)

        check(Conscrypt.isConscrypt(ssl)) { "Socket is not backed by Conscrypt!" }

        val pairCodeBytes = pairCode.toByteArray()
        val keyMaterial = Conscrypt.exportKeyingMaterial(
            ssl,
            kExportedKeyLabel,
            null,
            kExportedKeySize
        )

        val passwordBytes = pairCodeBytes + keyMaterial
        val context = PairingContext.create(passwordBytes)
        checkNotNull(context) { "Unable to create PairingContext." }

        this.pairingContext = context
        logger.log(AdbLogger.DEBUG, TAG, "TLS connection and key export complete.")
    }

    private fun createHeader(
        type: PairingPacketHeader.Type, payloadSize: Int
    ): PairingPacketHeader {
        return PairingPacketHeader(kCurrentKeyHeaderVersion, type.value, payloadSize)
    }

    private fun readHeader(): PairingPacketHeader? {
        val stream = inputStream ?: return null
        val bytes = ByteArray(kPairingPacketHeaderSize)
        stream.readFully(bytes)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return PairingPacketHeader.readFrom(buffer)
    }

    private fun writeHeader(header: PairingPacketHeader, payload: ByteArray) {
        val stream = outputStream ?: return
        val buffer = ByteBuffer.allocate(kPairingPacketHeaderSize).order(ByteOrder.BIG_ENDIAN)
        header.writeTo(buffer)

        stream.write(buffer.array())
        stream.write(payload)
    }

    private fun doExchangeMsgs(): Boolean {
        val ctx = pairingContext ?: return false
        val stream = inputStream ?: return false
        val msg = ctx.msg
        val size = msg.size

        val ourHeader = createHeader(PairingPacketHeader.Type.SPAKE2_MSG, size)
        writeHeader(ourHeader, msg)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.SPAKE2_MSG.value) return false

        val theirMessage = ByteArray(theirHeader.payload)
        stream.readFully(theirMessage)

        return ctx.initCipher(theirMessage)
    }

    private fun doExchangePeerInfo(): Boolean {
        val ctx = pairingContext ?: return false
        val stream = inputStream ?: return false
        val buf = ByteBuffer.allocate(kMaxPeerInfoSize).order(ByteOrder.BIG_ENDIAN)
        peerInfo.writeTo(buf)

        val outbuf = ctx.encrypt(buf.array()) ?: return false

        val ourHeader = createHeader(PairingPacketHeader.Type.PEER_INFO, outbuf.size)
        writeHeader(ourHeader, outbuf)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.PEER_INFO.value) return false

        val theirMessage = ByteArray(theirHeader.payload)
        stream.readFully(theirMessage)

        val decrypted =
            ctx.decrypt(theirMessage) ?: throw AdbInvalidPairingCodeException()
        if (decrypted.size != kMaxPeerInfoSize) {
            return false
        }
        return true
    }

    override fun close() {
        try {
            inputStream?.close()
        } catch (_: Throwable) {
        }
        try {
            outputStream?.close()
        } catch (_: Throwable) {
        }
        try {
            sslSocket?.close()
        } catch (_: Throwable) {
        }
        try {
            rawSocket?.close()
        } catch (_: Throwable) {
        }

        if (state != State.Ready) {
            pairingContext?.destroy()
        }
    }

    companion object {
        init {
            try {
                System.loadLibrary("adb")
            } catch (e: UnsatisfiedLinkError) {
                // native library error handled if JNI unavailable
            }
        }
    }
}
