# Branta Kotlin SDK

Kotlin SDK for [Branta](https://developer.branta.pro). Use `verify` on send and `addPayment` on receive. Status handling and parent platforms are in the [developer docs](https://developer.branta.pro).

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
        // For EVM stablecoins, token is on result.matchedDestinations[i].type
        // (e.g. UsdcBase). Pay from that list.
    }
    VerifyStatus.Unknown -> {
        // Not in Branta — show nothing.
    }
    VerifyStatus.Tampered -> {
        // Destination was altered. Warn. Do not show a verified card.
    }
    VerifyStatus.Incomplete -> {
        // 5xx, 429, unreachable, timeout, or malformed lookup. Tell the user.
        // Do not treat as unknown.
    }
}
```

Pass the scanned or pasted text to `verify`. A Bitcoin or EVM stablecoin lookup needs `branta_id` and `branta_secret` in the URI. bolt11, Ark, and silent payments resolve from the value. A bare address is `unknown`. The full rules are on [Status rules](https://developer.branta.pro/setup/send/status-rules), and supported chains and contracts are on [Destination types](https://developer.branta.pro/destination-types).

A 404 is `unknown`. 5xx, 429, no network, a timeout, or a malformed GET body is `incomplete`. `verifyUrl` is always returned. A logo on the wrong origin throws.

## Receive

```kotlin
val service = BrantaService(
    environment = BrantaEnvironment.Production,
    apiKey = "<api-key>"
)

val payment = PaymentBuilder()
    .setDescription("Invoice #12345")
    .addDestination("bc1q…", DestinationType.BitcoinAddress)
    // Stablecoin: pass the type explicitly — bare addresses are not auto-detected.
    // .addDestination("0x…", DestinationType.UsdtEthereum)
    .setTtl(600) // seconds. Omit to use the server default. 0 posts as 30 seconds.
    .build()

val result = service.addPayment(payment)
// Store result.secret with the invoice. Put branta_id and branta_secret on the QR
// (bitcoin:… or the ERC-681 ethereum:… URI).
```

Every destination is encrypted. `addPayment` encrypts a copy, so a failed post can be retried with the same `Payment`. Description is stored in encrypted metadata. `addPayment` accepts the types on [Destination types](https://developer.branta.pro/destination-types), and [Encryption](https://developer.branta.pro/setup/receive/encryption) says which key each uses. `UsdtTron`, `UsdtTon`, `UsdtSolana`, and `UsdcSolana` also encrypt and post, but no URI verifies them.

[Security](SECURITY.md)
