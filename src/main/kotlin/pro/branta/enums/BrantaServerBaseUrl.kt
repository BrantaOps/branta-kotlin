package pro.branta.enums

enum class BrantaServerBaseUrl(val url: String) {
    Staging("https://staging.guardrail.branta.pro"),
    Production("https://guardrail.branta.pro"),
    Localhost("http://localhost:3000")
}
