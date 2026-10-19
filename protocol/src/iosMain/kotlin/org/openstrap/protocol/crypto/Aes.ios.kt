package org.openstrap.protocol.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCOperation
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCSuccess
import platform.posix.size_tVar

/** AES-128-ECB without padding through Apple's CommonCrypto, the counterpart of `javax.crypto`. */
@OptIn(ExperimentalForeignApi::class)
actual object Aes {
    actual fun encrypt(key: ByteArray, data: ByteArray): ByteArray = crypt(kCCEncrypt, key, data)

    actual fun decrypt(key: ByteArray, data: ByteArray): ByteArray = crypt(kCCDecrypt, key, data)

    private fun crypt(op: CCOperation, key: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 16 && data.size % 16 == 0)
        if (data.isEmpty()) return ByteArray(0)
        val out = ByteArray(data.size)
        val status = memScoped {
            val moved = alloc<size_tVar>()
            val rc = key.usePinned { k ->
                data.usePinned { d ->
                    out.usePinned { o ->
                        CCCrypt(
                            op, kCCAlgorithmAES, kCCOptionECBMode,
                            k.addressOf(0), key.size.convert(), null,
                            d.addressOf(0), data.size.convert(),
                            o.addressOf(0), out.size.convert(), moved.ptr,
                        )
                    }
                }
            }
            check(rc == kCCSuccess) { "CCCrypt failed with status $rc" }
            check(moved.value.toLong() == data.size.toLong()) { "CCCrypt returned ${moved.value} of ${data.size} bytes" }
        }
        return out
    }
}
