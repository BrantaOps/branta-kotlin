package pro.branta

import pro.branta.enums.BrantaEnvironment

/** Per-call overrides. Every field is optional; unset fields keep the service defaults. */
data class BrantaApiOptions(
    val environment: BrantaEnvironment? = null,
    val apiKey: String? = null,
    /**
     * Secret from the Secrets tab in Guardrail. Signs `POST /v2/payments` with `X-HMAC-Signature` and
     * `X-HMAC-Timestamp`. Needed when your platform has Require HMAC on. Server-side only.
     * See https://developer.branta.pro/setup/receive/hmac-signing
     */
    val hmacSecret: String? = null
) {
    override fun toString(): String =
        "BrantaApiOptions(environment=$environment, apiKey=${redacted(apiKey)}, hmacSecret=${redacted(hmacSecret)})"
}

private fun redacted(value: String?): String = if (value.isNullOrEmpty()) "null" else "***"
