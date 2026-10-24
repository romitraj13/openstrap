package org.openstrap.app.device

import org.openstrap.protocol.auth.AuthException

/** What the Pair screen shows, and whether the key itself is the problem. */
data class PairingFailure(val message: String, val keyRejected: Boolean = false)

/**
 * Turns the strap's auth status codes (docs/protocol/01 §3.4) into what the user should do next.
 *
 * Copied from `app/src/main/kotlin/org/openstrap/app/device/PairingErrors.kt` @ 19f2865. The only
 * change is the return type: Android's `ConnectionState.Failed` does not exist here, so it is
 * [PairingFailure]. The status codes and the wording are unchanged.
 */
object PairingErrors {
    private const val WORKOUT_RUNNING = 5
    private const val NO_KEY = 7
    private const val SIGNATURE_FAILED = 8
    private const val AES_FAILED = 0x23
    private const val CIPHERTEXT_MISMATCH = 0x25
    private const val WRONG_KEY = 0x26
    private const val BINDING_PROTECTED = 0x41
    private const val CLOUD_BIND_REQUIRED = 0x81

    fun authFailure(e: AuthException): PairingFailure = when (e.status) {
        WRONG_KEY, CIPHERTEXT_MISMATCH, AES_FAILED -> PairingFailure(
            "The strap rejected this auth key. Use the key from the strap's most recent Zepp bind: every new bind " +
                "creates a new key, and keys from other devices on the account won't work.",
            keyRejected = true,
        )
        NO_KEY -> PairingFailure(
            "The strap has no key, which means it was unbound from Zepp or reset. Bind it in the Zepp app again, " +
                "get the new key, and don't unbind it afterwards.",
            keyRejected = true,
        )
        SIGNATURE_FAILED, BINDING_PROTECTED, CLOUD_BIND_REQUIRED -> PairingFailure(
            "The strap only accepts a key from a Zepp bind. Bind it in the Zepp app first, then use that bind's key.",
            keyRejected = true,
        )
        WORKOUT_RUNNING -> PairingFailure("A workout is running on the strap. End it, then tap Connect.")
        else -> PairingFailure(
            "Pairing failed (${e.message ?: "unknown error"}). Put the strap on its charger and tap Connect to try again.",
        )
    }
}
