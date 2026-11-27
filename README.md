# Branta Kotlin SDK

Kotlin SDK for [Branta](https://developer.branta.pro). Status handling and parent platforms are in the [developer docs](https://developer.branta.pro).

## Requirements

- JVM 11 / Android API 26 or higher
- Kotlin 2.x

## Install

[![Maven Central](https://img.shields.io/maven-central/v/pro.branta/branta)](https://central.sonatype.com/artifact/pro.branta/branta)

Use the latest version from Maven Central.

```kotlin
dependencies {
    implementation("pro.branta:branta:<version>")
}
```

## Send

`verify` is a suspend function. Scan and paste use the same call.

```kotlin
val service = BrantaService(environment = BrantaEnvironment.Production)

val result = service.verify(input)

when (result.status) {
    VerifyStatus.Match -> {
        // Show the card for result.payments.first(). Link it to result.verifyUrl.
        // For EVM stablecoins, token is on result.matchedDestinations[i].type. Pay from that list.
    }
    VerifyStatus.Unknown -> {
        // Not in Branta — show nothing.
    }
    VerifyStatus.Tampered -> {
        // Destination was altered. Warn. Do not show a verified card.
    }
    VerifyStatus.Incomplete -> {
        // Branta could not complete the check. Tell the user. Do not block send.
    }
}
```

Rules for each status: [Status rules](https://developer.branta.pro/setup/send/status-rules). Supported types: [Destination types](https://developer.branta.pro/destination-types).

## Receive

```kotlin
val service = BrantaService(
    environment = BrantaEnvironment.Production,
    apiKey = "<api-key>"
)

val payment = PaymentBuilder()
    .setDescription("Invoice #12345")
    .addDestination("bc1q…", DestinationType.BitcoinAddress)
    .setTtl(600) // seconds. Omit to use the server default. 0 posts as 30 seconds.
    .build()

val result = service.addPayment(payment)
// Store result.secret with the invoice. Put branta_id and branta_secret on the QR.
// Link the sender to result.verifyUrl.
```

`addPayment` accepts the types on [Destination types](https://developer.branta.pro/destination-types). Pass the type for stablecoins. A bare address is not auto-detected. `UsdtTron`, `UsdtTon`, `UsdtSolana`, and `UsdcSolana` also encrypt and post, but no URI verifies them.

### Brand

A shared-key platform tags each payment with the payee it was made for: a name plus optional logo source URLs.

```kotlin
val payment = PaymentBuilder()
    .addDestination("bc1q…", DestinationType.BitcoinAddress)
    .setBrand("Acme Coffee", logoUrl = "https://…/logo.png", logoLightUrl = "https://…/logo-light.png")
    .build()
```

`setChildPlatform` and `Payment.childPlatform` are the deprecated old names. They still work and delegate to `setBrand` / `brand`, but the SDK now sends `brand` on the wire, which needs a Branta server that accepts it.

### HMAC signing

If Branta enabled Require HMAC on your platform, create a Secret in Guardrail and set it as `hmacSecret`. The service signs each `addPayment`. Keep it server-side. See [HMAC signing](https://developer.branta.pro/setup/receive/hmac-signing).

```kotlin
val service = BrantaService(
    environment = BrantaEnvironment.Production,
    apiKey = "<api-key>",
    hmacSecret = "<secret>"
)
```
