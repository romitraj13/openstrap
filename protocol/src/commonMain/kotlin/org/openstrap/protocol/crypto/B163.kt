package org.openstrap.protocol.crypto

/**
 * ECDH on NIST B-163 (sect163r2), the curve the strap uses for its auth handshake.
 *
 * Field GF(2^163) with f(x) = x^163 + x^7 + x^6 + x^3 + 1, curve y^2 + xy = x^3 + x^2 + b.
 * Field elements are polynomials held in three Longs — bit i is bit i%64 of word i/64 — so the
 * module needs no java.math.BigInteger and compiles for every Kotlin target. Performance is
 * irrelevant here: one key generation and one shared-secret computation per connection.
 *
 * Wire format (docs/protocol/01 §3.2): scalars and coordinates are 24-byte little-endian.
 * Public key = X || Y (48 bytes, no prefix); shared secret = X coordinate (24 bytes).
 */
object B163 {
    const val M = 163

    /** x^7 + x^6 + x^3 + 1, the low part of f. */
    private const val REDUCER = 0xC9L

    /** Bit 163 inside the high word (x^163). */
    private const val X163 = 1L shl 35
    private const val BELOW_X163 = X163 - 1

    /** A GF(2^163) element: bit [i] is the coefficient of x^i. */
    data class F(val lo: Long, val mid: Long, val hi: Long) : Comparable<F> {
        val isZero: Boolean get() = lo == 0L && mid == 0L && hi == 0L

        /** Degree + 1 (0 for zero). */
        val bitLength: Int
            get() = when {
                hi != 0L -> 128 + (64 - hi.countLeadingZeroBits())
                mid != 0L -> 64 + (64 - mid.countLeadingZeroBits())
                else -> 64 - lo.countLeadingZeroBits()
            }

        fun bit(i: Int): Boolean = when {
            i < 64 -> (lo ushr i) and 1L != 0L
            i < 128 -> (mid ushr (i - 64)) and 1L != 0L
            i < 192 -> (hi ushr (i - 128)) and 1L != 0L
            else -> false
        }

        override fun compareTo(other: F): Int {
            // High words stay below 2^63, so signed comparisons order the value correctly.
            val h = hi.compareTo(other.hi)
            if (h != 0) return h
            val m = mid.compareTo(other.mid)
            if (m != 0) return m
            return lo.compareTo(other.lo)
        }

        companion object {
            val ZERO = F(0L, 0L, 0L)
            val ONE = F(1L, 0L, 0L)
        }
    }

    /** f as a field element: bit 163 set, so 164 bits wide. */
    private val MODULUS: F = F(REDUCER, 0L, X163)

    /** b of y^2 + xy = x^3 + x^2 + b. */
    val B: F = F(0x512f78744a3205fdL, 0xb8c953ca1481eb10uL.toLong(), 0x000000020a601907L)
    val GX: F = F(0xd4994637e8343e36uL.toLong(), 0x86a2d57ea0991168uL.toLong(), 0x00000003f0eba162L)
    val GY: F = F(0xb11c5c0c797324f1uL.toLong(), 0x71a0094fa2cdd545L, 0x00000000d51fbc6cL)

    /** Order of the generator; the private key is drawn from 1 until N-1. */
    val N: F = F(0x77e70c12a4234c33L, 0x00000000000292feL, 0x0000000400000000L)

    const val SCALAR_BYTES = 24
    const val PUBLIC_KEY_BYTES = 48

    /** An affine point; [INFINITY] is the identity (the point (0,0), which is not on the curve). */
    data class Point(val x: F, val y: F) {
        companion object { val INFINITY = Point(F.ZERO, F.ZERO) }
        val isInfinity get() = x.isZero && y.isZero
    }

    val G = Point(GX, GY)

    // --- GF(2^163) arithmetic -------------------------------------------------------------

    private fun add(a: F, b: F) = F(a.lo xor b.lo, a.mid xor b.mid, a.hi xor b.hi)

    /** a·x mod f. */
    private fun xtime(a: F): F {
        val lo = a.lo shl 1
        val mid = (a.mid shl 1) or (a.lo ushr 63)
        val hi = (a.hi shl 1) or (a.mid ushr 63)
        return if ((hi and X163) != 0L) F(lo xor REDUCER, mid, hi and BELOW_X163) else F(lo, mid, hi)
    }

    /** a / x (a right shift by one bit). */
    private fun xdiv(a: F) = F(
        (a.lo ushr 1) or (a.mid shl 63),
        (a.mid ushr 1) or (a.hi shl 63),
        a.hi ushr 1,
    )

    internal fun mul(a: F, b: F): F {
        var result = F.ZERO
        var x = a
        for (i in 0 until b.bitLength) {
            if (b.bit(i)) result = add(result, x)
            x = xtime(x)
        }
        return result
    }

    private fun sqr(a: F) = mul(a, a)

    /**
     * Inverse by the binary "almost inverse" algorithm: u and v walk Euclid's algorithm on
     * polynomials while g1 and g2 track the Bezout coefficients, staying inside 164 bits
     * because the halving step adds f back whenever the coefficient is odd.
     */
    internal fun inv(a: F): F {
        require(!a.isZero) { "Zero has no inverse" }
        if (a == F.ONE) return a
        var u = a
        var v = MODULUS
        var g1 = F.ONE
        var g2 = F.ZERO
        var guard = 0
        while (u != F.ONE) {
            while (!u.bit(0)) {
                check(!u.isZero) { "inverse: u reached zero" }
                check(guard++ < 64 * M) { "inverse did not converge" }
                u = xdiv(u)
                g1 = halve(g1)
            }
            while (!v.bit(0)) {
                check(!v.isZero) { "inverse: v reached zero" }
                check(guard++ < 64 * M) { "inverse did not converge" }
                v = xdiv(v)
                g2 = halve(g2)
            }
            // Shifting u can reach 1; combining then would make u^v zero when v is 1 too.
            if (u == F.ONE) break
            if (u.bitLength >= v.bitLength) {
                u = add(u, v)
                g1 = add(g1, g2)
            } else {
                v = add(v, u)
                g2 = add(g2, g1)
            }
        }
        return g1
    }

    /** g/2, adding f first when g is odd so the result stays a field element. */
    private fun halve(g: F) = xdiv(if (!g.bit(0)) g else add(g, MODULUS))

    // --- Curve arithmetic -----------------------------------------------------------------

    fun isOnCurve(p: Point): Boolean {
        if (p.isInfinity) return true
        if (p.x.bitLength > M || p.y.bitLength > M) return false
        val lhs = add(sqr(p.y), mul(p.x, p.y))
        val x2 = sqr(p.x)
        val rhs = add(add(mul(x2, p.x), x2), B)
        return lhs == rhs
    }

    fun pointAdd(p: Point, q: Point): Point {
        if (p.isInfinity) return q
        if (q.isInfinity) return p
        if (p.x == q.x) return if (p.y == q.y) pointDouble(p) else Point.INFINITY
        val lambda = mul(add(p.y, q.y), inv(add(p.x, q.x)))
        val x3 = add(add(add(add(sqr(lambda), lambda), p.x), q.x), F.ONE)
        val y3 = add(add(mul(lambda, add(p.x, x3)), x3), p.y)
        return Point(x3, y3)
    }

    fun pointDouble(p: Point): Point {
        if (p.isInfinity || p.x.isZero) return Point.INFINITY
        val lambda = add(p.x, mul(p.y, inv(p.x)))
        val x3 = add(add(sqr(lambda), lambda), F.ONE)
        val y3 = add(sqr(p.x), mul(add(lambda, F.ONE), x3))
        return Point(x3, y3)
    }

    fun multiply(k: F, p: Point): Point {
        var result = Point.INFINITY
        for (i in k.bitLength - 1 downTo 0) {
            result = pointDouble(result)
            if (k.bit(i)) result = pointAdd(result, p)
        }
        return result
    }

    // --- Encoding ------------------------------------------------------------------------

    fun toLe(v: F, size: Int = SCALAR_BYTES): ByteArray {
        require(v.bitLength <= size * 8) { "Value too large for $size bytes" }
        return ByteArray(size) { i ->
            val bit = i * 8
            val word = when {
                bit < 64 -> v.lo ushr bit
                bit < 128 -> v.mid ushr (bit - 64)
                else -> v.hi ushr (bit - 128)
            }
            word.toByte()
        }
    }

    fun fromLe(bytes: ByteArray): F {
        require(bytes.size <= 32) { "Too many bytes for a field element" }
        var lo = 0L
        var mid = 0L
        var hi = 0L
        for (i in bytes.indices) {
            val b = (bytes[i].toInt() and 0xFF).toLong()
            when {
                i < 8 -> lo = lo or (b shl (8 * i))
                i < 16 -> mid = mid or (b shl (8 * (i - 8)))
                i < 24 -> hi = hi or (b shl (8 * (i - 16)))
                else -> require(b == 0L) { "Value too large for a field element" }
            }
        }
        return F(lo, mid, hi)
    }

    fun encodePoint(p: Point): ByteArray = toLe(p.x) + toLe(p.y)

    fun decodePoint(bytes: ByteArray): Point {
        require(bytes.size == PUBLIC_KEY_BYTES) { "Public key must be $PUBLIC_KEY_BYTES bytes" }
        val p = Point(fromLe(bytes.copyOfRange(0, 24)), fromLe(bytes.copyOfRange(24, 48)))
        require(isOnCurve(p)) { "Device public key is not on B-163" }
        return p
    }

    // --- ECDH ----------------------------------------------------------------------------

    class KeyPair(val privateKey: F) {
        val publicKey: ByteArray = encodePoint(multiply(privateKey, G))
    }

    fun generateKeyPair(random: (Int) -> ByteArray = ::secureRandomBytes): KeyPair {
        while (true) {
            val bytes = random(SCALAR_BYTES)
            require(bytes.size == SCALAR_BYTES) { "Random source returned ${bytes.size} bytes" }
            // A 163-bit scalar: the top five bits of the 24-byte value are not part of the field.
            val raw = fromLe(bytes)
            val k = F(raw.lo, raw.mid, raw.hi and BELOW_X163)
            if (!k.isZero && k < N) return KeyPair(k)
        }
    }

    /** Shared secret = X coordinate of priv·peer, 24 bytes little-endian. */
    fun sharedSecret(privateKey: F, peerPublic: ByteArray): ByteArray {
        val s = multiply(privateKey, decodePoint(peerPublic))
        check(!s.isInfinity) { "Shared secret is the point at infinity" }
        return toLe(s.x)
    }
}
