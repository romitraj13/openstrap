package org.openstrap.protocol.crypto

/**
 * AES-128-ECB without padding, the only block primitive the strap uses (docs/protocol/01 §3.3).
 * JVM and Android use `javax.crypto`; iOS uses CommonCrypto's `CCCrypt`.
 */
expect object Aes {
    fun encrypt(key: ByteArray, data: ByteArray): ByteArray
    fun decrypt(key: ByteArray, data: ByteArray): ByteArray
}
