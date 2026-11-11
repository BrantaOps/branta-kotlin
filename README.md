# Branta Kotlin SDK

Kotlin SDK for [Branta](https://developer.branta.pro). Use `verify` on send and `addPayment` on receive.

## Requirements

- JVM 11 / Android API 26 or higher
- Kotlin 2.x

## Install

```kotlin
dependencies {
    implementation("pro.branta:branta:4.2.0")
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
        // For pol: rails, token is on result.matchedDestinations[i].type
        // (usdt_polygon / usdc_polygon) — do not infer it from pol:.
    }
    VerifyStatus.Unknown -> {
        // Not in Branta — show nothing.
    }
    VerifyStatus.Tampered -> {
        // Destination was altered. Warn. Do not show a verified card.
    }
    VerifyStatus.Incomplete -> {
        // 5xx, 429, or unreachable. Tell the user. Do not treat as unknown.
    }
}
```

On-chain Bitcoin needs a `bitcoin:` URI with `branta_id` / `branta_secret`. Stablecoin rails need a chain URI with the same params — `pol:` (`usdt_polygon`, `usdc_polygon`), `eth:` (`usdt_ethereum`, `usdc_ethereum`), `bsc:` (`usdt_bsc`, `usdc_bsc`), `tron:` (`usdt_tron`), `ton:` (`usdt_ton`, `usdc_ton`), `sol:` (`usdt_solana`, `usdc_solana`). Token is on `matchedDestinations[].type`, not the URI scheme; verify is match-if-any within the scheme's family. bolt11, Ark, and silent payments resolve from the value. A bare address (including bare `0x`) is `unknown`.

A 404 is `unknown`. 5xx, 429, or no network is `incomplete`. `verifyUrl` is always returned.

## Receive

```kotlin
val service = BrantaService(
    environment = BrantaEnvironment.Production,
    apiKey = "<api-key>"
)

val payment = PaymentBuilder()
    .setDescription("Invoice #12345")
    .addDestination("bc1q…", DestinationType.BitcoinAddress)
    // Polygon: pass UsdtPolygon / UsdcPolygon explicitly — bare 0x is not auto-detected.
    // .addDestination("0x…", DestinationType.UsdtPolygon)
    .setTtl(600) // seconds. Omit to use the server default. 0 posts as 30 seconds.
    .build()

val result = service.addPayment(payment)
// Store result.secret with the invoice. Put branta_id and branta_secret on the QR
// (bitcoin:… or pol:…).
```

Every destination is encrypted. `addPayment` encrypts a copy, so a failed post can be retried with the same `Payment`. Description is stored in encrypted metadata. `addPayment` accepts `BitcoinAddress`, `Bolt11`, `ArkAddress`, `SilentPayment`, `UsdtPolygon`, `UsdcPolygon`, `UsdtEthereum`, `UsdcEthereum`, `UsdtBsc`, `UsdcBsc`, `UsdtTron`, `UsdtTon`, `UsdcTon`, `UsdtSolana`, and `UsdcSolana`.

Shared-key parent platforms tag the child on the builder:

```kotlin
PaymentBuilder()
    .setDescription("Invoice #12345")
    .addDestination("bc1q…", DestinationType.BitcoinAddress)
    .setChildPlatform("ChildBrand", logoUrl = "https://example.com/logo.png")
    .build()
```

Per-client keys are deprecated. A per-call `BrantaApiOptions(apiKey = "<child-api-key>")` still selects the child key.

## Publishing

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

## Responsible Disclosure

Found critical bugs or vulnerabilities? Email them to support@branta.pro.
