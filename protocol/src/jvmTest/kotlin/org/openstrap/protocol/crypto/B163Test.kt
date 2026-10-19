package org.openstrap.protocol.crypto

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom

class B163Test {
    private val bc = SECNamedCurves.getByName("sect163r2")

    // B163 now holds field elements in three Longs instead of java.math.BigInteger, so the
    // BouncyCastle cross-check converts at the boundary. The assertions are unchanged.
    private fun B163.F.toBigInteger(): BigInteger = BigInteger(1, B163.toLe(this).reversedArray())

    private fun BigInteger.toF(): B163.F =
        B163.fromLe(ByteArray(B163.SCALAR_BYTES) { i -> this.shiftRight(8 * i).toByte() })

    @Test
    fun curveConstantsMatchBouncyCastle() {
        assertEquals(bc.curve.b.toBigInteger(), B163.B.toBigInteger())
        assertEquals(bc.g.normalize().affineXCoord.toBigInteger(), B163.GX.toBigInteger())
        assertEquals(bc.g.normalize().affineYCoord.toBigInteger(), B163.GY.toBigInteger())
        assertEquals(bc.n, B163.N.toBigInteger())
    }

    @Test
    fun generatorIsOnCurveAndHasOrderN() {
        assertTrue(B163.isOnCurve(B163.G))
        assertTrue(B163.multiply(B163.N, B163.G).isInfinity)
    }

    @Test
    fun fieldInverse() {
        val a = BigInteger("123456789abcdef0123456789abcdef01234567", 16)
        assertEquals(BigInteger.ONE, B163.mul(a.toF(), B163.inv(a.toF())).toBigInteger())
    }

    @Test
    fun scalarMultiplicationMatchesBouncyCastle() {
        val rnd = SecureRandom()
        repeat(5) {
            val k = BigInteger(160, rnd)
            val ours = B163.multiply(k.toF(), B163.G)
            val theirs = bc.g.multiply(k).normalize()
            assertEquals(theirs.affineXCoord.toBigInteger(), ours.x.toBigInteger())
            assertEquals(theirs.affineYCoord.toBigInteger(), ours.y.toBigInteger())
        }
    }

    @Test
    fun ecdhIsSymmetricAndUsesLittleEndian() {
        val a = B163.generateKeyPair()
        val b = B163.generateKeyPair()
        assertEquals(48, a.publicKey.size)
        val s1 = B163.sharedSecret(a.privateKey, b.publicKey)
        val s2 = B163.sharedSecret(b.privateKey, a.publicKey)
        assertArrayEquals(s1, s2)
        assertEquals(24, s1.size)

        // Generator encoded little-endian: Gx's lowest byte (0x36) comes first.
        val g = B163.encodePoint(B163.G)
        assertEquals(0x36.toByte(), g[0])
        assertEquals(0x00.toByte(), g[23])
    }
}
