package pro.branta

import pro.branta.enums.BrantaEnvironment

/** Per-call overrides. Every field is optional; unset fields keep the service defaults. */
data class BrantaApiOptions(
    val environment: BrantaEnvironment? = null,
    val apiKey: String? = null,
    @property:Deprecated("Use a shared apiKey with PaymentBuilder.setChildPlatform instead.")
    val hmacSecret: String? = null
)
