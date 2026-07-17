package pro.branta.v2.interfaces

interface IAesEncryption {
    fun encrypt(value: String, secret: String, deterministicNonce: Boolean = false): String
    fun decrypt(encryptedValue: String, secret: String): String
}
