package pro.branta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pro.branta.v2.AesEncryption
import pro.branta.v2.toNormalizedHash
import java.util.Base64

class AesEncryptionTest {

    @Test
    fun `encrypt and decrypt round-trip returns original value`() {
        val value = "test-value"
        val secret = "test-secret"
        assertEquals(value, AesEncryption.decrypt(AesEncryption.encrypt(value, secret), secret))
    }

    @Test
    fun `encrypt with wrong secret fails decryption`() {
        val encrypted = AesEncryption.encrypt("value", "correct-secret")
        assertThrows<Exception> { AesEncryption.decrypt(encrypted, "wrong-secret") }
    }

    @Test
    fun `encrypt with random nonce produces different ciphertext each time`() {
        val first = AesEncryption.encrypt("value", "secret", deterministicNonce = false)
        val second = AesEncryption.encrypt("value", "secret", deterministicNonce = false)
        assertNotEquals(first, second)
    }

    @Test
    fun `encrypt with deterministic nonce produces same ciphertext each time`() {
        val first = AesEncryption.encrypt("value", "secret", deterministicNonce = true)
        val second = AesEncryption.encrypt("value", "secret", deterministicNonce = true)
        assertEquals(first, second)
    }

    @Test
    fun `decrypt throws IllegalArgumentException on data too short`() {
        val shortBase64 = Base64.getEncoder().encodeToString(ByteArray(10))
        assertThrows<IllegalArgumentException> { AesEncryption.decrypt(shortBase64, "secret") }
    }

    @Test
    fun `deterministic nonce round-trip decrypts correctly for bolt11`() {
        val value = "lnbc100n1ptest"
        val key = value.toNormalizedHash()
        assertEquals(value, AesEncryption.decrypt(AesEncryption.encrypt(value, key, deterministicNonce = true), key))
    }

    @Test
    fun `unicode value round-trips correctly`() {
        val value = "café ☕"
        val secret = "unicode-secret"
        assertEquals(value, AesEncryption.decrypt(AesEncryption.encrypt(value, secret), secret))
    }
}
