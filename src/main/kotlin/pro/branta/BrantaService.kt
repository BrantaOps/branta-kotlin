package pro.branta

import okhttp3.OkHttpClient
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.enums.VerifyStatus
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.exceptions.IncompleteException
import pro.branta.models.AddPaymentResult
import pro.branta.models.Destination
import pro.branta.models.Payment
import pro.branta.models.VerifyResult
import java.util.UUID

private class TamperedException : Exception()

private fun toVerifyResult(payments: List<Payment>, verifyUrl: String): VerifyResult =
    if (payments.isEmpty()) VerifyResult(VerifyStatus.Unknown, verifyUrl)
    else VerifyResult(VerifyStatus.Match, verifyUrl, payments)

private fun copyPayment(payment: Payment): Payment = payment.copy(
    destinations = payment.destinations.map { it.copy() }.toMutableList(),
    parentPlatform = payment.parentPlatform?.copy(),
    childPlatform = payment.childPlatform?.copy()
)

private val bech32Regex = Regex("^(bc|tb|bcrt)1", RegexOption.IGNORE_CASE)

private fun addressesMatch(a: String, b: String): Boolean {
    fun isBech32(value: String) = bech32Regex.containsMatchIn(value)
    return if (isBech32(a) && isBech32(b)) a.equals(b, ignoreCase = true) else a == b
}

private fun uniformOnChainAddress(parser: QrParser): String? {
    val addresses = parser.destinations
        .filter { it.type == DestinationType.BitcoinAddress && it.value.isNotBlank() }
        .map { it.value }
    if (addresses.isEmpty()) return null
    val first = addresses.first()
    for (address in addresses.drop(1)) {
        if (!addressesMatch(first, address)) throw TamperedException()
    }
    return first
}

class BrantaService internal constructor(
    private val defaultOptions: BrantaApiOptions,
    private val client: BrantaClient,
    private val secretGenerator: SecretGenerator
) {
    constructor(
        environment: BrantaEnvironment,
        apiKey: String? = null,
        hmacSecret: String? = null,
        httpClient: OkHttpClient? = null
    ) : this(
        BrantaApiOptions(environment = environment, apiKey = apiKey, hmacSecret = hmacSecret),
        HttpBrantaClient(
            BrantaApiOptions(environment = environment, apiKey = apiKey, hmacSecret = hmacSecret),
            httpClient ?: OkHttpClient()
        ),
        UuidSecretGenerator()
    )

    suspend fun verify(input: String, options: BrantaApiOptions? = null): VerifyResult {
        val parser = QrParser(input)
        val lookup = parser.onChainEncryptionText ?: parser.destination ?: ""
        try {
            val expectedOnChainAddress = uniformOnChainAddress(parser)
            if (parser.hasUnverifiableInstruction) {
                return VerifyResult(VerifyStatus.Unknown, buildVerifyUrl(options, lookup))
            }
            if (parser.hasBrantaParams()) {
                val additionalValues = parser.destinations
                    .filter { it.value.getDerivedKeyType() != null }
                    .map { it.value }
                if (expectedOnChainAddress == null && additionalValues.isEmpty()) {
                    return VerifyResult(VerifyStatus.Unknown, buildVerifyUrl(options, lookup))
                }
                val lookedUp = lookupByBrantaId(
                    parser.onChainEncryptionText!!,
                    parser.onChainEncryptionSecret,
                    additionalValues,
                    expectedOnChainAddress,
                    options
                )
                return toVerifyResult(lookedUp.first, lookedUp.second)
            }
            val destination = parser.destination
                ?: return VerifyResult(VerifyStatus.Unknown, buildVerifyUrl(options, lookup))
            if (destination.getDerivedKeyType() == null) {
                return VerifyResult(VerifyStatus.Unknown, buildVerifyUrl(options, destination))
            }
            val lookedUp = lookupByDerivedKey(destination, options)
            return toVerifyResult(lookedUp.first, lookedUp.second)
        } catch (_: TamperedException) {
            return VerifyResult(VerifyStatus.Tampered, buildVerifyUrl(options, lookup))
        } catch (_: IncompleteException) {
            return VerifyResult(VerifyStatus.Incomplete, buildVerifyUrl(options, lookup))
        }
    }

    private suspend fun lookupByBrantaId(
        lookupValue: String,
        encryptionKey: String?,
        additionalHashValues: List<String>,
        expectedOnChainAddress: String?,
        options: BrantaApiOptions?
    ): Pair<List<Payment>, String> {
        val payments = client.getPayments(lookupValue, options)
        val keys = linkedMapOf<String, String>()
        val bound = mutableListOf<Payment>()
        for (payment in payments) {
            val onChainBound = decryptDestinations(payment, encryptionKey, keys, expectedOnChainAddress)
            var hashesBound = true
            for (value in additionalHashValues) {
                if (!decryptDerivedKeyDestinations(payment, value, keys)) hashesBound = false
            }
            val hasPayableInstruction = expectedOnChainAddress != null || additionalHashValues.isNotEmpty()
            if (hasPayableInstruction && onChainBound && hashesBound) bound.add(payment)
        }
        return bound to buildVerifyUrl(options, lookupValue, keys)
    }

    private fun decryptDerivedKeyDestinations(
        payment: Payment,
        plainValue: String,
        keys: MutableMap<String, String>
    ): Boolean {
        val derivedType = plainValue.getDerivedKeyType() ?: return false
        val key = plainValue.toNormalizedHash()
        var bound = false
        for (destination in payment.destinations) {
            if (!destination.isEncrypted || destination.type != derivedType) continue
            val decrypted = try {
                AesEncryption.decrypt(destination.value, key)
            } catch (_: Exception) {
                continue
            }
            if (!decrypted.equals(plainValue, ignoreCase = true)) throw TamperedException()
            destination.value = decrypted
            destination.isEncrypted = false
            destination.id?.let { keys.putIfAbsent(it, key) }
            tryDecryptMetadata(payment, destination, key)
            bound = true
        }
        return bound
    }

    private suspend fun lookupByDerivedKey(
        destinationValue: String,
        options: BrantaApiOptions?
    ): Pair<List<Payment>, String> {
        val normalizedDestination = destinationValue.lowercase()
        val key = normalizedDestination.toNormalizedHash()
        val lookupValue = AesEncryption.encrypt(normalizedDestination, key, deterministicNonce = true)
        val payments = client.getPayments(lookupValue, options)
        val keys = linkedMapOf<String, String>()
        val bound = mutableListOf<Payment>()
        for (payment in payments) {
            if (decryptDerivedKeyDestinations(payment, normalizedDestination, keys)) bound.add(payment)
        }
        return bound to buildVerifyUrl(options, lookupValue, keys)
    }

    private fun decryptDestinations(
        payment: Payment,
        encryptionKey: String?,
        keys: MutableMap<String, String>,
        expectedOnChainAddress: String?
    ): Boolean {
        var onChainBound = expectedOnChainAddress == null
        for (destination in payment.destinations) {
            if (!destination.isEncrypted) continue
            if (destination.type != DestinationType.BitcoinAddress) continue
            if (encryptionKey == null) continue
            val decrypted = try {
                AesEncryption.decrypt(destination.value, encryptionKey)
            } catch (_: Exception) {
                continue
            }
            if (expectedOnChainAddress != null && !addressesMatch(decrypted, expectedOnChainAddress)) {
                throw TamperedException()
            }
            if (expectedOnChainAddress != null) onChainBound = true
            destination.value = decrypted
            destination.isEncrypted = false
            destination.id?.let { keys.putIfAbsent(it, encryptionKey) }
            tryDecryptMetadata(payment, destination, encryptionKey)
        }
        return onChainBound
    }

    private fun tryDecryptMetadata(payment: Payment, destination: Destination, keyUsed: String) {
        val encryptedDek = destination.encryptedDek ?: return
        if (payment.metadata == null || payment.isMetadataDecrypted) return
        try {
            val dek = AesEncryption.decrypt(encryptedDek, keyUsed)
            payment.metadata = AesEncryption.decrypt(payment.metadata!!, dek)
            payment.isMetadataDecrypted = true
            liftDescriptionFromMetadata(payment)
        } catch (_: Exception) {
            // DEK decryption failed — leave metadata as-is.
        }
    }

    suspend fun addPayment(payment: Payment, options: BrantaApiOptions? = null): AddPaymentResult {
        val submitted = copyPayment(payment)
        foldDescriptionIntoMetadata(submitted)

        var dek: String? = null
        if (submitted.metadata != null && submitted.destinations.isNotEmpty()) {
            dek = secretGenerator.generate()
            submitted.metadata = AesEncryption.encrypt(submitted.metadata!!, dek, deterministicNonce = false)
        }

        val secret = secretGenerator.generate()
        val encryptedToKey = linkedMapOf<String, String>()
        for (destination in submitted.destinations) {
            if (destination.id == null) destination.id = UUID.randomUUID().toString()
            val detected = destination.type ?: detectDestinationType(destination.value)
            if (destination.type == null && detected != null) destination.type = detected
            if (detected == DestinationType.BitcoinAddress) {
                destination.value = AesEncryption.encrypt(
                    destination.value,
                    secret,
                    deterministicNonce = secretGenerator.deterministicNonce
                )
                encryptedToKey[destination.value] = secret
                if (dek != null) destination.encryptedDek = AesEncryption.encrypt(dek, secret, deterministicNonce = false)
            } else {
                val derivedType = destination.value.getDerivedKeyType()
                    ?: throw BrantaPaymentException("destination type '${destination.type}' cannot be encrypted")
                val normalizedValue = destination.value.lowercase()
                val key = normalizedValue.toNormalizedHash()
                destination.value = AesEncryption.encrypt(normalizedValue, key, deterministicNonce = true)
                encryptedToKey[destination.value] = key
                if (dek != null) destination.encryptedDek = AesEncryption.encrypt(dek, key, deterministicNonce = false)
            }
        }

        val responsePayment = client.postPayment(submitted, options)
            ?: throw BrantaPaymentException("No payment returned from server.")
        val keys = linkedMapOf<String, String>()
        for (destination in responsePayment.destinations) {
            val id = destination.id ?: continue
            encryptedToKey[destination.value]?.let { keys[id] = it }
        }
        val primaryValue = submitted.destinations.firstOrNull()?.value ?: ""
        return AddPaymentResult(responsePayment, secret, buildVerifyUrl(options, primaryValue, keys))
    }

    suspend fun isApiKeyValid(options: BrantaApiOptions? = null): Boolean = client.isApiKeyValid(options)

    private fun buildVerifyUrl(
        options: BrantaApiOptions?,
        paymentLookup: String,
        keys: Map<String, String> = emptyMap()
    ): String {
        val baseUrl = defaultOptions.environmentUrl(options)
        var url = "$baseUrl/v2/verify/${encodeUriComponent(paymentLookup)}"
        if (keys.isNotEmpty()) url += keys.toUrlFragment()
        return url
    }
}
