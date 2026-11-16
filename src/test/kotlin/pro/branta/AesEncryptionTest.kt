package pro.branta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class AesEncryptionTest {

    // Fixed vectors. Every Branta SDK asserts the same strings, so a payment written by one SDK
    // stays readable and findable by every other. Do not regenerate these from this code.
    @Test
    fun `encrypt fixed vector matches every sdk`() {
        assertEquals(
            "mPIKHc3ywVlsBHf3Lv2Rwpz2+fKE0kgUePq2m4fPIUidMuGEHVIB",
            AesEncryption.encrypt("hello world", "my-secret", deterministicNonce = true),
        )
    }

    @Test
    fun `decrypt fixed vector returns plaintext`() {
        assertEquals(
            "hello world",
            AesEncryption.decrypt("mPIKHc3ywVlsBHf3Lv2Rwpz2+fKE0kgUePq2m4fPIUidMuGEHVIB", "my-secret"),
        )
    }

    @Test
    fun `encrypt derived key fixed vector matches every sdk`() {
        // Key is the normalized hash of "LNBC10U1PTestInvoice". The value is encrypted lowercased.
        val key = "261D7974DC0541402A222AC17973D3FF412878C1C179FCD4D866094D610B4496"
        assertEquals(
            "WywBRexAiHTi2mnHK/W1DIN79bF1XVhU3BdC3DWpUsdhs5NMbdyZyzWsdjfuFgIV",
            AesEncryption.encrypt("lnbc10u1ptestinvoice", key, deterministicNonce = true),
        )
    }

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
