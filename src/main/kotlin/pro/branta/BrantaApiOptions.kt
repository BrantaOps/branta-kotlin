package pro.branta

import pro.branta.enums.BrantaEnvironment

/** Per-call overrides. Every field is optional; unset fields keep the service defaults. */
data class BrantaApiOptions(
    val environment: BrantaEnvironment? = null,
    val apiKey: String? = null,
    val hmacSecret: String? = null
)
