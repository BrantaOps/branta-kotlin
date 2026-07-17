package pro.branta.v2

import pro.branta.enums.DestinationType
import java.net.URLDecoder

data class QrDestination(val value: String, val type: DestinationType?)

class QrParser(qrText: String) {
    val destinations: MutableList<QrDestination> = mutableListOf()
    var onChainEncryptionText: String? = null
    var onChainEncryptionSecret: String? = null

    val destination: String? get() = destinations.firstOrNull()?.value
    val destinationType: DestinationType? get() = destinations.firstOrNull()?.type

    init {
        val text = qrText.trim()
        val scheme = text.substringBefore(":", "").lowercase()

        if (scheme == "bitcoin" || scheme == "lightning") {
            val addr = extractAddress(text)
            if (addr != null) {
                destinations.add(QrDestination(addr, getDestinationType(text)))
            }

            val queryString = text.substringAfter("?", "")
            val params = parseQueryString(queryString)

            onChainEncryptionText = params["branta_id"]
            onChainEncryptionSecret = params["branta_secret"]

            params["lightning"]?.let { destinations.add(QrDestination(it, detectPlainTextType(it))) }
            params["bolt12"]?.let { destinations.add(QrDestination(it, detectPlainTextType(it))) }
            params["ark"]?.let { destinations.add(QrDestination(it, detectPlainTextType(it))) }
            params["silent_payment"]?.let { destinations.add(QrDestination(it, detectPlainTextType(it))) }
        } else if (text.contains(":")) {
            // Unknown scheme — add as-is with no type
            destinations.add(QrDestination(text, null))
        } else {
            destinations.add(QrDestination(text, detectPlainTextType(text)))
        }
    }

    fun isOnChainZk(): Boolean = onChainEncryptionText != null && onChainEncryptionSecret != null

    private fun extractAddress(text: String): String? {
        val colonIdx = text.indexOf(':')
        if (colonIdx < 0) return null
        val questionIdx = text.indexOf('?', colonIdx)
        val addr = if (questionIdx < 0) text.substring(colonIdx + 1) else text.substring(colonIdx + 1, questionIdx)
        return addr.ifEmpty { null }
    }

    private fun getDestinationType(text: String): DestinationType? {
        val scheme = text.substringBefore(":").lowercase()
        if (scheme == "bitcoin") return DestinationType.BitcoinAddress
        if (scheme == "lightning") {
            val dest = extractAddress(text) ?: return null
            if (dest.isBolt11()) return DestinationType.Bolt11
            if (dest.startsWith("lno", ignoreCase = true)) return DestinationType.Bolt12
            if (dest.startsWith("LNURL", ignoreCase = true)) return DestinationType.LnUrl
        }
        return null
    }

    private fun parseQueryString(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&")
            .filter { it.isNotEmpty() }
            .mapNotNull { param ->
                val eqIdx = param.indexOf('=')
                if (eqIdx < 0) null
                else {
                    val key = URLDecoder.decode(param.substring(0, eqIdx), "UTF-8").lowercase()
                    val value = URLDecoder.decode(param.substring(eqIdx + 1), "UTF-8")
                    key to value
                }
            }
            .toMap()
    }

    companion object {
        private val ethAddressRegex = Regex("^0x[0-9a-fA-F]{40}$")
        private val lnAddressRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        internal fun detectPlainTextType(value: String): DestinationType? = when {
            value.isBolt11() -> DestinationType.Bolt11
            value.startsWith("lno", ignoreCase = true) -> DestinationType.Bolt12
            value.startsWith("LNURL", ignoreCase = true) -> DestinationType.LnUrl
            value.isArk() -> DestinationType.ArkAddress
            value.isSilentPayment() -> DestinationType.SilentPayment
            ethAddressRegex.matches(value) -> DestinationType.TetherAddress
            value.length == 34 && value.startsWith('T') -> DestinationType.TetherAddress
            lnAddressRegex.matches(value) -> DestinationType.LnAddress
            value.startsWith("1") || value.startsWith("3") ||
                value.startsWith("bc1", ignoreCase = true) -> DestinationType.BitcoinAddress
            else -> null
        }
    }
}
