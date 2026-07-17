package pro.branta.v2

import pro.branta.v2.interfaces.IAesEncryption

class AesEncryptionService : IAesEncryption {
    override fun encrypt(value: String, secret: String, deterministicNonce: Boolean): String =
        AesEncryption.encrypt(value, secret, deterministicNonce)

    override fun decrypt(encryptedValue: String, secret: String): String =
        AesEncryption.decrypt(encryptedValue, secret)
}
