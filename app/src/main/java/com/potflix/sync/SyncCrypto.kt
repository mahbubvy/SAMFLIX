package com.potflix.sync

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The sync code shared by a user's devices, and the encryption built from it. Every message
 * is sealed with AES-GCM under a key derived from the code, so a device without the code can
 * neither read the library nor slip changes into it.
 */
object SyncCrypto {
    // No 0/O, 1/I/L: the code is typed by hand.
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val CODE_LENGTH = 10
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun newCode(): String = buildString { repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Upper-cases and strips spaces and dashes; returns null unless it is a valid code. */
    fun normalize(input: String): String? {
        val code = input.uppercase().filter { it.isLetterOrDigit() }
        return code.takeIf { it.length == CODE_LENGTH && it.all { c -> c in ALPHABET } }
    }

    /** "ABCDE23456" → "ABCDE-23456", for display. */
    fun format(code: String): String = code.chunked(5).joinToString("-")

    fun keyFor(code: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("samflix-library-sync-v1:$code".toByteArray(Charsets.UTF_8))

    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    /** Returns null when the message wasn't sealed with this key (another code) or was altered. */
    fun open(key: ByteArray, sealed: ByteArray): ByteArray? {
        if (sealed.size <= IV_BYTES) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (e: AEADBadTagException) {
            null
        }
    }
}
