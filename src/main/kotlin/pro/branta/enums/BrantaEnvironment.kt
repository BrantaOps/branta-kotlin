package pro.branta.enums

enum class BrantaEnvironment(val url: String) {
    Staging("https://staging.guardrail.branta.pro"),
    Production("https://guardrail.branta.pro"),
    Localhost("http://localhost:3000")
}
