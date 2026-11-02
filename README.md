# Branta Kotlin SDK

Kotlin SDK for [Branta](https://developer.branta.pro). Use `verify` on send and `addPayment` on receive.

## Requirements

- JVM 11 / Android API 26 or higher
- Kotlin 2.x

## Install

```kotlin
dependencies {
    implementation("pro.branta:branta:4.0.0")
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

On-chain Bitcoin needs a `bitcoin:` URI with `branta_id` / `branta_secret`. bolt11, Ark, and silent payments resolve from the value. A bare address is `unknown`.

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
    .setTtl(600) // seconds. Omit to use the server default. 0 posts as 30 seconds.
    .build()

val result = service.addPayment(payment)
// Store result.secret with the invoice. Put branta_id and branta_secret on the QR.
```

Every destination is encrypted. `addPayment` encrypts a copy, so a failed post can be retried with the same `Payment`. Description is stored in encrypted metadata.

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
