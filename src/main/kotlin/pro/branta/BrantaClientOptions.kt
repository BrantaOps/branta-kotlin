package pro.branta

import pro.branta.enums.BrantaServerBaseUrl
import pro.branta.enums.PrivacyMode

data class BrantaClientOptions(
    val baseUrl: BrantaServerBaseUrl? = null,
    val defaultApiKey: String? = null,
    val hmacSecret: String? = null,
    val privacy: PrivacyMode? = null
)
