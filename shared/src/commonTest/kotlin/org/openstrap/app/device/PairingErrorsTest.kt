package org.openstrap.app.device

import org.openstrap.protocol.auth.AuthException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Copied from `app/src/main/kotlin/org/openstrap/app/device/PairingErrors.kt` @ 19f2865. The Android
 * `ConnectionState.Failed` return type became [PairingFailure]; the status codes and wording are the
 * same, because Romit reads them.
 */
class PairingErrorsTest {
    @Test
    fun wrongKeyIsReportedAsARejectedKey() {
        val failure = PairingErrors.authFailure(AuthException(0x26, "Authentication failed: wrong key"))
        assertTrue(failure.keyRejected)
        assertTrue(failure.message.contains("rejected this auth key"), failure.message)
    }

    @Test
    fun noKeyOnTheStrapAsksForARebind() {
        val failure = PairingErrors.authFailure(AuthException(7, "Authentication failed: no key"))
        assertTrue(failure.keyRejected)
        assertTrue(failure.message.contains("no key"), failure.message)
    }

    @Test
    fun runningWorkoutIsNotAKeyProblem() {
        val failure = PairingErrors.authFailure(AuthException(5, "Authentication failed: busy"))
        assertFalse(failure.keyRejected)
        assertEquals("A workout is running on the strap. End it, then tap Connect.", failure.message)
    }

    @Test
    fun unknownStatusAsksForAnotherAttempt() {
        val failure = PairingErrors.authFailure(AuthException(0x7f, "Authentication failed: ?"))
        assertFalse(failure.keyRejected)
        assertTrue(failure.message.contains("Pairing failed (Authentication failed: ?)"), failure.message)
    }
}
