package pro.branta.v2

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesEncryption {

    fun encrypt(value: String, secret: String, deterministicNonce: Boolean = false): String {
        try {
            val keyData = sha256(secret.toByteArray(Charsets.UTF_8))

            val iv = ByteArray(12)
            if (deterministicNonce) {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(keyData, "HmacSHA256"))
                val derived = mac.doFinal(value.toByteArray(Charsets.UTF_8))
                System.arraycopy(derived, 0, iv, 0, 12)
            } else {
                SecureRandom().nextBytes(iv)
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyData, "AES"), GCMParameterSpec(128, iv))
            // doFinal returns ciphertext + 16-byte auth tag concatenated
            val cipherWithTag = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

            // Wire format: [12-byte IV][ciphertext+tag] — matches .NET layout exactly
            val result = ByteArray(12 + cipherWithTag.size)
            System.arraycopy(iv, 0, result, 0, 12)
            System.arraycopy(cipherWithTag, 0, result, 12, cipherWithTag.size)

            return Base64.getEncoder().encodeToString(result)
        } catch (e: Exception) {
            throw Exception("Encryption failed: ${e.message}", e)
        }
    }

    fun decrypt(encryptedValue: String, secret: String): String {
        val encryptedData = Base64.getDecoder().decode(encryptedValue)

        if (encryptedData.size < 28)
            throw IllegalArgumentException("Invalid encrypted data: too short")

        try {
            val keyData = sha256(secret.toByteArray(Charsets.UTF_8))
            val iv = encryptedData.copyOfRange(0, 12)
            // Remainder is ciphertext + 16-byte auth tag — Java GCM accepts this directly
            val cipherWithTag = encryptedData.copyOfRange(12, encryptedData.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyData, "AES"), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(cipherWithTag), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw Exception("Decryption failed: ${e.message}", e)
        }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}
