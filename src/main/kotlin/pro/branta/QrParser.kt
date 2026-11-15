package pro.branta

import pro.branta.enums.DestinationType
import java.net.URI

internal data class QrDestination(
    val value: String,
    /** Rail this value belongs to. Set for bitcoin and ERC-681 transfers. */
    val type: DestinationType? = null,
    /** Visible address that participates in the secret-key swap check. */
    val secretKey: Boolean = false
)

internal fun normalizeQrText(qrText: String): String {
    val trimmed = qrText.trim { it.code <= 0x20 }
    return trimmed.replace(Regex("[\\t\\n\\r]"), "")
}

internal fun detectDestinationType(value: String): DestinationType? {
    if (value.isBolt11()) return DestinationType.Bolt11
    if (value.isArk()) return DestinationType.ArkAddress
    if (value.isSilentPayment()) return DestinationType.SilentPayment
    val lower = value.lowercase()
    if (value.startsWith("1") || value.startsWith("3") ||
        lower.startsWith("bc1") || lower.startsWith("tb1") || lower.startsWith("bcrt1")
    ) {
        return DestinationType.BitcoinAddress
    }
    return null
}

internal fun parseQueryString(query: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    val trimmed = if (query.startsWith("?")) query.substring(1) else query
    if (trimmed.isEmpty()) return result
    for (part in trimmed.split("&")) {
        if (part.isEmpty()) continue
        val idx = part.indexOf('=')
        val rawKey = if (idx == -1) part else part.substring(0, idx)
        val rawValue = if (idx == -1) "" else part.substring(idx + 1)
        if (rawKey.isEmpty()) continue
        try {
            val key = decodeUriComponent(rawKey).lowercase()
            val value = decodeUriComponent(rawValue)
            result.putIfAbsent(key, value)
        } catch (_: IllegalArgumentException) {
            // skip malformed parameter
        }
    }
    return result
}

private fun queryFromText(text: String): String {
    // A "?" after the "#" belongs to the fragment, not the query.
    val hash = text.indexOf('#')
    val beforeFragment = if (hash == -1) text else text.substring(0, hash)
    val q = beforeFragment.indexOf('?')
    if (q == -1) return ""
    return beforeFragment.substring(q)
}

private fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'

private fun isAsciiHex(c: Char): Boolean = isAsciiDigit(c) || c in 'a'..'f' || c in 'A'..'F'

/** Path of an `ethereum:` URI, without scheme, query, or fragment. One leading slash is dropped. */
private fun erc681Path(text: String): String {
    var path = text.substring(text.indexOf(':') + 1)
    val cut = path.indexOfFirst { it == '?' || it == '#' }
    if (cut != -1) path = path.substring(0, cut)
    return if (path.startsWith("/")) path.substring(1) else path
}

/** `0x` or `0X` followed by 40 hex digits. */
private fun isEvmAddress(value: String): Boolean =
    value.length == 42 && value[0] == '0' && (value[1] == 'x' || value[1] == 'X') &&
        value.substring(2).all(::isAsciiHex)

private data class Erc681Transfer(val type: DestinationType, val address: String)

/**
 * ERC-681 token transfer. Chain id and contract select one destination type.
 * The deposit address is the `address` query param.
 */
private fun parseErc681(text: String): Erc681Transfer? {
    val path = erc681Path(text)
    val at = path.indexOf('@')
    if (at == -1) return null
    val contract = path.substring(0, at)
    // The contract prefix is a lowercase `0x`, then 40 hex digits.
    if (contract.length != 42 || !contract.startsWith("0x") || !contract.substring(2).all(::isAsciiHex)) return null
    val rest = path.substring(at + 1)
    val suffix = "/transfer"
    if (rest.length <= suffix.length || !rest.endsWith(suffix)) return null
    val chain = rest.substring(0, rest.length - suffix.length)
    if (chain.isEmpty() || !chain.all(::isAsciiDigit)) return null
    val digits = chain.trimStart('0')
    // No supported chain id has more than 15 digits.
    if (digits.length > 15) return null
    val chainId = if (digits.isEmpty()) 0L else digits.toLong()
    val type = DestinationType.evmTokenType(chainId, contract) ?: return null
    val address = parseQueryString(queryFromText(text))["address"] ?: return null
    if (!isEvmAddress(address)) return null
    return Erc681Transfer(type, address)
}

private fun destinationFromText(text: String): String? =
    Regex("(?<=:)[^?]*").find(text)?.value

internal class QrParser(qrText: String) {
    val destinations: MutableList<QrDestination> = mutableListOf()
    var onChainEncryptionText: String? = null
    var onChainEncryptionSecret: String? = null
    /**
     * Destination types this scan may decrypt.
     * ERC-681 sets one type from the chain id and token contract.
     * Bitcoin sets `BitcoinAddress`.
     */
    var expectedSecretKeyTypes: List<DestinationType> = emptyList()
    var hasUnverifiableInstruction: Boolean = false

    val destination: String? get() = destinations.firstOrNull()?.value
    val destinationType: DestinationType? get() = destinations.firstOrNull()?.type

    init {
        val text = normalizeQrText(qrText)
        val uri = runCatching { URI(text) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || scheme == null) {
            destinations.add(QrDestination(text, detectDestinationType(text)))
        } else if (scheme == "ethereum") {
            val transfer = parseErc681(text)
            if (transfer == null) {
                destinations.add(QrDestination(text, null))
            } else {
                destinations.add(QrDestination(transfer.address, transfer.type, secretKey = true))
                expectedSecretKeyTypes = listOf(transfer.type)
                val queryParams = parseQueryString(queryFromText(text))
                onChainEncryptionText = queryParams["branta_id"]
                onChainEncryptionSecret = queryParams["branta_secret"]
            }
        } else if (scheme == "bitcoin" || scheme == "lightning") {
            val dest = destinationFromText(text)
            if (dest != null) {
                val destType = destinationTypeOf(text)
                destinations.add(
                    QrDestination(
                        dest,
                        destType,
                        secretKey = destType == DestinationType.BitcoinAddress
                    )
                )
                if (dest.isNotEmpty() &&
                    destType != DestinationType.BitcoinAddress &&
                    dest.getDerivedKeyType() == null
                ) {
                    hasUnverifiableInstruction = true
                }
            }

            val queryParams = parseQueryString(queryFromText(text))
            onChainEncryptionText = queryParams["branta_id"]
            onChainEncryptionSecret = queryParams["branta_secret"]

            fun addBindable(value: String?, type: DestinationType?, onChain: Boolean) {
                if (value == null) return
                if (value.isNotEmpty() && !onChain && value.getDerivedKeyType() == null) {
                    hasUnverifiableInstruction = true
                    return
                }
                destinations.add(QrDestination(value, type, secretKey = onChain))
            }

            addBindable(queryParams["lightning"], detectDestinationType(queryParams["lightning"] ?: ""), false)
            addBindable(queryParams["ark"], detectDestinationType(queryParams["ark"] ?: ""), false)
            addBindable(queryParams["silent_payment"], detectDestinationType(queryParams["silent_payment"] ?: ""), false)
            addBindable(queryParams["bc"], DestinationType.BitcoinAddress, true)
            addBindable(queryParams["tb"], DestinationType.BitcoinAddress, true)
            addBindable(queryParams["sp"], detectDestinationType(queryParams["sp"] ?: ""), false)
            if (queryParams.containsKey("bolt12")) hasUnverifiableInstruction = true
            if (queryParams.containsKey("lno")) hasUnverifiableInstruction = true
            if (queryParams.containsKey("pay")) hasUnverifiableInstruction = true

            if (destinations.any { it.secretKey }) {
                expectedSecretKeyTypes = listOf(DestinationType.BitcoinAddress)
            }
        } else {
            destinations.add(QrDestination(text, null))
        }
    }

    fun hasBrantaParams(): Boolean =
        onChainEncryptionText != null && onChainEncryptionSecret != null

    private fun destinationTypeOf(text: String): DestinationType? {
        if (text.startsWith("bitcoin:", ignoreCase = true)) return DestinationType.BitcoinAddress
        if (text.startsWith("lightning:", ignoreCase = true)) {
            val dest = destinationFromText(text)
            if (dest != null && dest.isBolt11()) return DestinationType.Bolt11
        }
        return null
    }
}
