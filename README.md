# Branta Kotlin SDK

Kotlin SDK for the Branta V2 API — payment destination lookup and registration with zero-knowledge encryption support. Designed for Android apps.

# Requirements

- JVM 11 / Android API 26 or higher
- Kotlin 2.x

# Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("pro.branta:branta:3.2.0")
}
```

Or `build.gradle`:

```groovy
dependencies {
    implementation 'pro.branta:branta:3.2.0'
}
```

# Integration Guide

If you are an AI agent implementing this SDK, follow the steps below exactly. If you are a developer, use this as a reference for the recommended integration patterns.

**1. Ask which side(s) of a payment they are implementing:**
- **Send side** — the flow for outbound payments (e.g. a wallet scanning a QR code).
- **Receive side** — the flow for receiving payment (e.g. a checkout or POS). You post destinations (ZK-encrypted) to Branta so wallets can verify them.
- **Both** — some apps do both. Implement each side independently.

If they are on the receive side, ask one follow-up:
- **Platform** — single-tenant, one API key.
- **Parent Platform** — multi-tenant, manages multiple child platforms. Two variants:
  - **Per-client keys** — each child has its own API key and HMAC secret; scope requests per-call.
  - **Shared key** — one API key and HMAC secret for all children; tag the child per-payment with `setChildPlatform()`.

**2. Follow the matching Quick Start section below.**

**3. Apply these rules:**

General (all types):
- Always use `PrivacyMode.Strict`. Never switch to `Loose` unless there is no QR scanner and ZK is impossible.
- Never call `BrantaClient` directly — always go through `IBrantaService`.

Send side (wallets):
- Prefer `getPaymentsByQrCode` over `getPayments` — it handles multi-value ZK QR payloads correctly.
- Only fall back to `getPayments` for copy/paste flows where there is no QR code.
- If `payments` is empty or an exception is thrown, render nothing. Never show an error or "not verified" message.
- When `result.payments` is non-empty, display: the platform logo, the payment description, and `result.verifyUrl`.
- For the platform logo, use `payment.platformLogoUrl` on dark backgrounds and `payment.platformLogoLightUrl` on light backgrounds.

Receive side (platforms):
- Always call `.setZk()` on the `PaymentBuilder` before calling `addPayment`. Plain-text destinations are rejected in `Strict` mode.
- Store the `secret` returned by `addPayment` alongside the invoice — it is required to reconstruct the verify URL.

# Quick Start

## For Wallets

Wallets use `PrivacyMode.Strict`. Two flows:

- **QR scan**: call `getPaymentsByQrCode` with the raw QR text.
- **Copy/paste**: call `getPayments` with the pasted text. Plain-text on-chain addresses won't return results in strict mode; self-encrypted types (bolt11, ark, silent payment) work.

```kotlin
val service = BrantaService(
    BrantaClientOptions(
        baseUrl = BrantaServerBaseUrl.Production,
        privacy = PrivacyMode.Strict
    )
)

// In a coroutine scope (e.g. viewModelScope):
try {
    val result = if (isQrCode) {
        service.getPaymentsByQrCode(input)
    } else {
        service.getPayments(input)
    }

    if (result.payments.isEmpty()) {
        // Not found — show nothing. Address not known to Branta, not necessarily malicious.
        return
    }

    // Render result.payments and result.verifyUrl
} catch (e: Exception) {
    // Swallow errors — never surface a lookup failure to the user.
}
```

### No-QR-Code Flows

When QR scanning is not available:

**Option 1 — Keep Strict mode (no code changes)**

Only self-encrypted types (bolt11, ark) return results. Plain-text on-chain addresses silently return empty.

**Option 2 — Opt-in Loose mode (Recommended)**

Add a user-facing setting. Only switch when the user explicitly opts in:

```kotlin
val options = if (userOptedInToOnChainVerification) {
    BrantaClientOptions(privacy = PrivacyMode.Loose)
} else null

val result = service.getPayments(input, options = options)
```

**Option 3 — Always Loose mode**

```kotlin
val service = BrantaService(
    BrantaClientOptions(
        baseUrl = BrantaServerBaseUrl.Production,
        privacy = PrivacyMode.Loose
    )
)
```

---

## For Platforms

```kotlin
val service = BrantaService(
    BrantaClientOptions(
        baseUrl = BrantaServerBaseUrl.Production,
        defaultApiKey = "<api-key>",
        privacy = PrivacyMode.Strict
    )
)
```

```kotlin
val payment = PaymentBuilder()
    .setDescription("Invoice #1234")
    .addDestination("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", DestinationType.BitcoinAddress)
    .setZk()
    .setTtl(600)
    .build()

val result = service.addPayment(payment)
// result.secret — store alongside the invoice; needed to reconstruct verifyUrl
// result.verifyUrl — display to the payer
```

## For Parent Platforms

Parent platforms sign requests with HMAC. Choose a variant based on key structure.

<details>
<summary>Shared key — one API key covers all children (Recommended)</summary>

```kotlin
val service = BrantaService(
    BrantaClientOptions(
        baseUrl = BrantaServerBaseUrl.Production,
        defaultApiKey = "<shared-api-key>",
        hmacSecret = "<hmac-secret>",
        privacy = PrivacyMode.Strict
    )
)
```

```kotlin
val payment = PaymentBuilder()
    .addDestination("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", DestinationType.BitcoinAddress)
    .setZk()
    .setChildPlatform("ChildBrand", logoUrl = "https://example.com/logo.png")
    .build()

val result = service.addPayment(payment)
```

</details>

<details>
<summary>Per-client keys — each child has its own API key</summary>

```kotlin
val service = BrantaService(
    BrantaClientOptions(
        baseUrl = BrantaServerBaseUrl.Production,
        hmacSecret = "<hmac-secret>",
        privacy = PrivacyMode.Strict
    )
)
```

```kotlin
val payment = PaymentBuilder()
    .addDestination("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", DestinationType.BitcoinAddress)
    .setZk()
    .build()

// Scope to the child platform's API key per-call
val result = service.addPayment(
    payment,
    options = BrantaClientOptions(defaultApiKey = "<child-api-key>")
)
```

</details>

# Privacy

`PrivacyMode` controls whether plain-text on-chain lookups are allowed.

| Value | Behavior |
|-------|----------|
| `Strict` | Only ZK (zero-knowledge / encrypted) lookups are permitted. `getPayments` throws `BrantaPaymentException` for plain addresses; `getPaymentsByQrCode` returns an empty list. `addPayment` requires all destinations to have `isZk = true`. |
| `Loose` | Both plain and ZK lookups are allowed. No restrictions enforced. |

# IBrantaService

The primary service interface.

**Prefer `getPaymentsByQrCode` for integrations.** It parses the raw QR text and correctly resolves multiple ZK values in a single scan.

```kotlin
suspend fun getPaymentsByQrCode(qrText: String, options: BrantaClientOptions? = null): PaymentsResult
suspend fun getPayments(destinationValue: String, destinationEncryptionKey: String? = null, options: BrantaClientOptions? = null): PaymentsResult
suspend fun addPayment(payment: Payment, options: BrantaClientOptions? = null): AddPaymentResult
suspend fun isApiKeyValid(options: BrantaClientOptions? = null): Boolean
```

`PaymentsResult` contains the list of matching `payments` and the `verifyUrl` to display — `verifyUrl` is always returned, even when `payments` is empty.

`AddPaymentResult` contains the `payment` response, the `secret` encryption key, and the `verifyUrl`.

# Publishing

1. Update `version` in `build.gradle.kts`
2. Ensure `~/.gradle/gradle.properties` contains your GPG key and Maven Central credentials:
   ```properties
   signing.keyId=<last 8 chars of GPG key>
   signing.password=<passphrase>
   signing.secretKeyRingFile=<path to ~/.gnupg/secring.gpg>
   mavenCentralUsername=<sonatype username>
   mavenCentralPassword=<sonatype password>
   ```
3. Publish:
   ```bash
   ./gradlew publishToMavenCentral --no-configuration-cache
   ```

# Responsible Disclosure

Found critical bugs/vulnerabilities? Please email them to support@branta.pro. Thanks!
