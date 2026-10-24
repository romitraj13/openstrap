package org.openstrap.protocol.crypto

/**
 * [n] cryptographically secure random bytes. `java.security.SecureRandom` on the JVM and Android,
 * `SecRandomCopyBytes` on iOS.
 */
expect fun secureRandomBytes(n: Int): ByteArray
