package org.openstrap.protocol.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/** Apple's system CSPRNG, the counterpart of `java.security.SecureRandom`. */
@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomBytes(n: Int): ByteArray {
    require(n >= 0) { "Negative byte count" }
    val out = ByteArray(n)
    if (n == 0) return out
    val status = out.usePinned { SecRandomCopyBytes(kSecRandomDefault, n.convert(), it.addressOf(0)) }
    check(status == 0) { "SecRandomCopyBytes failed with status $status" }
    return out
}
