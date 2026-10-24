package org.openstrap.protocol.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** AES-128-ECB without padding through the JDK provider — the code the Android app shipped with. */
actual object Aes {
    actual fun encrypt(key: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 16 && data.size % 16 == 0)
        return Cipher.getInstance("AES/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            doFinal(data)
        }
    }

    actual fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 16 && data.size % 16 == 0)
        return Cipher.getInstance("AES/ECB/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
            doFinal(data)
        }
    }
}
