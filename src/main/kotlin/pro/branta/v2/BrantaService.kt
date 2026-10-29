package pro.branta.v2

import pro.branta.BrantaClientOptions
import pro.branta.enums.DestinationType
import pro.branta.enums.PrivacyMode
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.exceptions.BrantaPaymentExceptionReason
import pro.branta.v2.interfaces.IAesEncryption
import pro.branta.v2.interfaces.IBrantaClient
import pro.branta.v2.interfaces.IBrantaService
import pro.branta.v2.interfaces.ISecretGenerator
import pro.branta.v2.models.AddPaymentResult
import pro.branta.v2.models.Destination
import pro.branta.v2.models.Payment
import pro.branta.v2.models.PaymentsResult
import java.net.URLEncoder

private const val TAMPERED_MESSAGE =
    "The Bitcoin address in the QR code does not match the address verified by Branta. The QR code may have been tampered with."

private val bech32Regex = Regex("^(bc|tb|bcrt)1", RegexOption.IGNORE_CASE)

class BrantaService(
    private val defaultOptions: BrantaClientOptions? = null,
    private val client: IBrantaClient = BrantaClient(defaultOptions),
    private val aesEncryption: IAesEncryption = AesEncryptionService(),
    private val secretGenerator: ISecretGenerator = GuidSecretGenerator()
) : IBrantaService {

    private fun addressesMatch(a: String, b: String): Boolean {
        fun isBech32(v: String) = bech32Regex.containsMatchIn(v)
        return if (isBech32(a) && isBech32(b)) a.equals(b, ignoreCase = true) else a == b
    }

    private fun tampered() = BrantaPaymentException(TAMPERED_MESSAGE, BrantaPaymentExceptionReason.Tampered)

    private fun uniformOnChainAddress(parser: QrParser): String? {
        val addresses = parser.destinations
            .filter { it.type == DestinationType.BitcoinAddress && it.value.isNotBlank() }
            .map { it.value }
        if (addresses.isEmpty()) return null
        val first = addresses.first()
        for (address in addresses.drop(1)) {
            if (!addressesMatch(first, address)) throw tampered()
        }
        return first
    }

    override suspend fun getPaymentsByQrCode(qrText: String, options: BrantaClientOptions?): PaymentsResult {
        val parser = QrParser(qrText)
        val expectedOnChainAddress = uniformOnChainAddress(parser)

        if (parser.hasUnverifiableInstruction) {
            val lookup = parser.onChainEncryptionText ?: parser.destination ?: ""
            return PaymentsResult(emptyList(), buildVerifyUrl(options, lookup))
        }

        if (parser.isOnChainZk()) {
            val additionalValues = parser.destinations
                .filter { it.value.getHashZkType() != null }
                .map { it.value }
            return getPaymentsForZk(parser.onChainEncryptionText!!, parser.onChainEncryptionSecret, additionalValues, expectedOnChainAddress, options)
        }

        val destination = parser.destination ?: return PaymentsResult(emptyList(), buildVerifyUrl(options, ""))

        if (defaultOptions.getPrivacy(options) == PrivacyMode.Strict && destination.getHashZkType() == null) {
            return PaymentsResult(emptyList(), buildVerifyUrl(options, destination))
        }

        return getPayments(destination, null, options)
    }

    private suspend fun getPaymentsForZk(
        lookupValue: String,
        encryptionKey: String?,
        additionalHashValues: List<String>,
        expectedOnChainAddress: String?,
        options: BrantaClientOptions?
    ): PaymentsResult {
        val payments = client.getPayments(lookupValue, options)
        val keys = mutableMapOf<String, String>()
        val bound = mutableListOf<Payment>()

        for (payment in payments) {
            val onChainBound = decryptDestinations(payment, lookupValue, encryptionKey, null, keys, expectedOnChainAddress)
            var hashesBound = true
            for (value in additionalHashValues) {
                if (!decryptHashZkDestinations(payment, value, keys)) hashesBound = false
            }
            if (onChainBound && hashesBound) bound.add(payment)
        }

        return PaymentsResult(bound, buildVerifyUrl(options, lookupValue, keys))
    }

    private fun decryptHashZkDestinations(payment: Payment, plainValue: String, keys: MutableMap<String, String>): Boolean {
        val hashZkType = plainValue.getHashZkType() ?: return false
        val key = plainValue.toNormalizedHash()
        var bound = false

        for (destination in payment.destinations) {
            if (!destination.isZk || destination.type != hashZkType) continue
            try {
                destination.value = aesEncryption.decrypt(destination.value, key)
                destination.isEncrypted = false
                destination.zkId?.let { keys.putIfAbsent(it, key) }
                tryDecryptMetadata(payment, destination, key)
                bound = true
            } catch (_: Exception) {
                // Key didn't match this destination — leave it encrypted.
            }
        }
        return bound
    }

    override suspend fun getPayments(
        destinationValue: String,
        destinationEncryptionKey: String?,
        options: BrantaClientOptions?
    ): PaymentsResult {
        val hashZkType = destinationValue.getHashZkType()

        if (hashZkType == null && destinationEncryptionKey == null &&
            defaultOptions.getPrivacy(options) == PrivacyMode.Strict
        ) {
            throw BrantaPaymentException("PrivacyMode.Strict does not permit plain-text lookups for this destination type.")
        }

        val normalizedDestination = if (hashZkType != null) destinationValue.lowercase() else destinationValue
        var lookupValue = if (hashZkType != null) {
            aesEncryption.encrypt(normalizedDestination, normalizedDestination.toNormalizedHash(), deterministicNonce = true)
        } else {
            destinationValue
        }

        var payments = client.getPayments(lookupValue, options)

        if (payments.isEmpty() && hashZkType != null && defaultOptions.getPrivacy(options) != PrivacyMode.Strict) {
            lookupValue = normalizedDestination
            payments = client.getPayments(lookupValue, options)
        }

        val keys = mutableMapOf<String, String>()
        for (payment in payments) {
            decryptDestinations(payment, normalizedDestination, destinationEncryptionKey, hashZkType, keys)
        }

        return PaymentsResult(payments, buildVerifyUrl(options, lookupValue, keys))
    }

    private fun decryptDestinations(
        payment: Payment,
        destinationValue: String,
        encryptionKey: String?,
        hashZkType: DestinationType?,
        keys: MutableMap<String, String>,
        expectedOnChainAddress: String? = null
    ): Boolean {
        var onChainBound = expectedOnChainAddress == null
        for (destination in payment.destinations) {
            destination.isEncrypted = destination.isZk
            if (!destination.isZk) continue

            if (destination.type == DestinationType.BitcoinAddress) {
                if (encryptionKey == null) continue
                val decrypted: String
                try {
                    decrypted = aesEncryption.decrypt(destination.value, encryptionKey)
                } catch (_: Exception) {
                    // Key didn't match — leave encrypted.
                    continue
                }

                if (expectedOnChainAddress != null && !addressesMatch(decrypted, expectedOnChainAddress)) {
                    throw tampered()
                }

                if (expectedOnChainAddress != null) onChainBound = true
                destination.value = decrypted
                destination.isEncrypted = false
                destination.zkId?.let { keys.putIfAbsent(it, encryptionKey) }
                tryDecryptMetadata(payment, destination, encryptionKey)
            } else if (hashZkType != null && destination.type == hashZkType) {
                val key = destinationValue.toNormalizedHash()
                try {
                    destination.value = aesEncryption.decrypt(destination.value, key)
                    destination.isEncrypted = false
                    destination.zkId?.let { keys.putIfAbsent(it, key) }
                    tryDecryptMetadata(payment, destination, key)
                } catch (_: Exception) {
                    // Key didn't match — leave encrypted.
                }
            }
        }
        return onChainBound
    }

    private fun tryDecryptMetadata(payment: Payment, destination: Destination, keyUsed: String) {
        val encDek = destination.encryptedDek ?: return
        val metadata = payment.metadata ?: return
        if (payment.isMetadataDecrypted) return
        try {
            val dek = aesEncryption.decrypt(encDek, keyUsed)
            payment.metadata = aesEncryption.decrypt(metadata, dek)
            payment.isMetadataDecrypted = true
        } catch (_: Exception) {
            // DEK decryption failed — leave metadata as-is.
        }
    }

    override suspend fun addPayment(payment: Payment, options: BrantaClientOptions?): AddPaymentResult {
        if (defaultOptions.getPrivacy(options) == PrivacyMode.Strict &&
            payment.destinations.any { !it.isZk }
        ) {
            throw BrantaPaymentException("PrivacyMode.Strict requires all destinations to be ZK; one or more destinations have isZk = false.")
        }

        var dek: String? = null
        if (payment.metadata != null && payment.destinations.any { it.isZk }) {
            dek = secretGenerator.generate()
            payment.metadata = aesEncryption.encrypt(payment.metadata!!, dek)
        }

        val secret = secretGenerator.generate()
        val encryptedToKey = mutableMapOf<String, String>()

        for (destination in payment.destinations) {
            if (!destination.isZk) continue

            if (destination.type == DestinationType.BitcoinAddress) {
                destination.value = aesEncryption.encrypt(destination.value, secret, secretGenerator.deterministicNonce)
                encryptedToKey[destination.value] = secret
                if (dek != null) destination.encryptedDek = aesEncryption.encrypt(dek, secret)
            } else {
                val hashZkType = destination.value.getHashZkType()
                    ?: throw BrantaPaymentException("destination type '${destination.type}' does not support ZK")

                val normalizedValue = destination.value.lowercase()
                val key = normalizedValue.toNormalizedHash()
                destination.value = aesEncryption.encrypt(normalizedValue, key, deterministicNonce = true)
                encryptedToKey[destination.value] = key
                if (dek != null) destination.encryptedDek = aesEncryption.encrypt(dek, key)
            }
        }

        val responsePayment = client.postPayment(payment, options)
            ?: throw BrantaPaymentException("No payment returned from server.")

        val keys = responsePayment.destinations
            .filter { it.zkId != null && encryptedToKey.containsKey(it.value) }
            .associate { it.zkId!! to encryptedToKey[it.value]!! }

        val primaryValue = payment.destinations.firstOrNull()?.value ?: ""
        val verifyUrl = buildVerifyUrl(options, primaryValue, keys)

        return AddPaymentResult(responsePayment, secret, verifyUrl)
    }

    override suspend fun isApiKeyValid(options: BrantaClientOptions?): Boolean =
        client.isApiKeyValid(options)

    private fun buildVerifyUrl(options: BrantaClientOptions?, paymentLookup: String, keys: Map<String, String> = emptyMap()): String {
        val baseUrl = defaultOptions.getBaseUrl(options)
        val encoded = URLEncoder.encode(paymentLookup, "UTF-8")
        var url = "$baseUrl/v2/verify/$encoded"
        if (keys.isNotEmpty()) url += keys.toUrlFragment()
        return url
    }
}
