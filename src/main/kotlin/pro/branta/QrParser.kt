package pro.branta

import pro.branta.enums.DestinationType
import java.net.URI

internal data class QrDestination(val value: String, val type: DestinationType?)

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
    val q = text.indexOf('?')
    if (q == -1) return ""
    val hash = text.indexOf('#', q)
    return text.substring(q, if (hash == -1) text.length else hash)
}

private fun destinationFromText(text: String): String? =
    Regex("(?<=:)[^?]*").find(text)?.value

internal class QrParser(qrText: String) {
    val destinations: MutableList<QrDestination> = mutableListOf()
    var onChainEncryptionText: String? = null
    var onChainEncryptionSecret: String? = null
    var hasUnverifiableInstruction: Boolean = false

    val destination: String? get() = destinations.firstOrNull()?.value
    val destinationType: DestinationType? get() = destinations.firstOrNull()?.type

    init {
        val text = normalizeQrText(qrText)
        val uri = runCatching { URI(text) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || scheme == null) {
            destinations.add(QrDestination(text, detectDestinationType(text)))
        } else if (scheme == "bitcoin" || scheme == "lightning") {
            val dest = destinationFromText(text)
            if (dest != null) {
                val destType = destinationTypeOf(text)
                destinations.add(QrDestination(dest, destType))
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
                destinations.add(QrDestination(value, type))
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
