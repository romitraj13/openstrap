package org.openstrap.protocol.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import org.openstrap.protocol.hexToBytes

class AesTest {
    @Test
    fun fips197AppendixC1() {
        // FIPS-197 appendix C.1: AES-128, ECB, no padding. Both directions.
        val key = "000102030405060708090a0b0c0d0e0f".hexToBytes()
        val plain = "00112233445566778899aabbccddeeff".hexToBytes()
        val cipher = "69c4e0d86a7b0430d8cdb78070b4c55a".hexToBytes()
        assertContentEquals(cipher, Aes.encrypt(key, plain))
        assertContentEquals(plain, Aes.decrypt(key, cipher))
    }
}
