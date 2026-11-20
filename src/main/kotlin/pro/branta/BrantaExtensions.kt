package pro.branta

import pro.branta.enums.DestinationType
import pro.branta.exceptions.BrantaPaymentException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

internal fun String.isBolt11(): Boolean =
    startsWith("lnbc", ignoreCase = true) ||
        startsWith("lntb", ignoreCase = true) ||
        startsWith("lnbcrt", ignoreCase = true)

internal fun String.isArk(): Boolean =
    startsWith("ark1", ignoreCase = true) || startsWith("tark1", ignoreCase = true)

internal fun String.isSilentPayment(): Boolean =
    startsWith("sp1", ignoreCase = true) || startsWith("tsp1", ignoreCase = true)

/** bolt11, Ark, and silent payments encrypt with a key derived from the value. */
internal fun String.getDerivedKeyType(): DestinationType? = when {
    isBolt11() -> DestinationType.Bolt11
    isArk() -> DestinationType.ArkAddress
    isSilentPayment() -> DestinationType.SilentPayment
    else -> null
}

internal fun String.toNormalizedHash(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(lowercase().toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}

/** `encodeURIComponent`: percent-encode, but leave `! ' ( ) *` and `~` alone. */
internal fun encodeUriComponent(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name())
        .replace("+", "%20")
        .replace("%21", "!")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%7E", "~")

/** `decodeURIComponent`. A `+` stays a plus sign. */
internal fun decodeUriComponent(value: String): String =
    URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())

internal fun Map<String, String>.toUrlFragment(): String {
    if (isEmpty()) return ""
    return "#" + entries.joinToString("&") { (key, value) ->
        "k-${encodeUriComponent(key)}=${encodeUriComponent(value)}"
    }
}

internal fun BrantaApiOptions?.environmentUrl(override: BrantaApiOptions?): String {
    val environment = override?.environment ?: this?.environment
        ?: throw BrantaPaymentException("Branta: environment is a required option.")
    return environment.url
}

internal fun BrantaApiOptions?.apiKey(override: BrantaApiOptions?): String? =
    override?.apiKey ?: this?.apiKey

internal fun BrantaApiOptions?.hmacSecret(override: BrantaApiOptions?): String? =
    override?.hmacSecret ?: this?.hmacSecret
