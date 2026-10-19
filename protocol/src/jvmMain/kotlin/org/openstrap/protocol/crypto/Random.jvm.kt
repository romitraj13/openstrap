package org.openstrap.protocol.crypto

import java.security.SecureRandom

private val secureRandom = SecureRandom()

actual fun secureRandomBytes(n: Int): ByteArray = ByteArray(n).also { secureRandom.nextBytes(it) }
