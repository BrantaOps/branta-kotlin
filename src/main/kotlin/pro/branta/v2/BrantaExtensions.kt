package pro.branta.v2

import pro.branta.BrantaClientOptions
import pro.branta.enums.DestinationType
import pro.branta.enums.PrivacyMode
import pro.branta.exceptions.BrantaPaymentException
import java.security.MessageDigest

fun String.isBolt11(): Boolean =
    startsWith("lnbc", ignoreCase = true) ||
    startsWith("lntb", ignoreCase = true) ||
    startsWith("lnbcrt", ignoreCase = true)

fun String.isArk(): Boolean = startsWith("ark1", ignoreCase = true)

fun String.isSilentPayment(): Boolean =
    startsWith("sp1", ignoreCase = true) ||
    startsWith("tsp1", ignoreCase = true)

fun String.getHashZkType(): DestinationType? = when {
    isBolt11() -> DestinationType.Bolt11
    isArk() -> DestinationType.ArkAddress
    isSilentPayment() -> DestinationType.SilentPayment
    else -> null
}

fun String.toNormalizedHash(): String {
    val normalized = lowercase()
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}

fun Map<String, String>.toUrlFragment(): String =
    "#" + entries.joinToString("&") { "k-${it.key}=${it.value}" }

fun BrantaClientOptions?.getBaseUrl(override: BrantaClientOptions?): String {
    val baseUrl = override?.baseUrl ?: this?.baseUrl
        ?: throw BrantaPaymentException("Branta: baseUrl is a required option.")
    return baseUrl.url
}

fun BrantaClientOptions?.getPrivacy(
    override: BrantaClientOptions?,
    fallback: PrivacyMode = PrivacyMode.Strict
): PrivacyMode = override?.privacy ?: this?.privacy ?: fallback

fun BrantaClientOptions?.getApiKey(override: BrantaClientOptions?): String? =
    override?.defaultApiKey ?: this?.defaultApiKey

fun BrantaClientOptions?.getHmacSecret(override: BrantaClientOptions?): String? =
    override?.hmacSecret ?: this?.hmacSecret
