# Branta Kotlin SDK

Kotlin SDK published to Maven Central as `pro.branta:branta`. Consumers (wallets, platforms, parent platforms) call `BrantaService` to look up or post payments to Branta. `README.md` is the install, constructor, and samples guide.

## Behavior contract

What `verify` returns and how ERC-681 is parsed: [Status rules](https://developer.branta.pro/setup/send/status-rules). Which rails encrypt with what key: [Encryption](https://developer.branta.pro/setup/receive/encryption). Send handling is on [Send](https://developer.branta.pro/setup/send), and the type list on [Destination types](https://developer.branta.pro/destination-types). Do not restate them here. Change the docs first, then the SDK.

Invariants that break silently. Check them before you change `verify` or `addPayment`:

- A raw on-chain address (including `tb1` / `bcrt1` / bare `0x`) is `unknown` and does not GET.
- A swapped address is `tampered`. `unknown`, `tampered`, and `incomplete` are returned, never thrown.
- The token is `matchedDestinations[].type`, never the URI scheme.
- A logo-domain mismatch throws. Every other lookup failure is a status.
- `addPayment` encrypts a copy and leaves the caller's `Payment` unchanged.
- The payment POST sends `X-Branta-SDK: <language>/<version>`. A GET never does. [SDK header](https://developer.branta.pro/tech/api/receive#sdk-header)
- Keep parity with every 4.x Branta SDK. A public method added or changed here is mirrored in each of them.

## Kotlin specifics

- A logo-domain mismatch throws `BrantaPaymentException`.
- `DestinationType` is only what `addPayment` accepts. It also includes `usdt_tron`, `usdt_ton`, `usdt_solana`, and `usdc_solana`. They can be posted but no URI verifies them, and they are not on the public Destination types page. There is no `usdc_ton`.
- `hmacSecret` signs `POST /v2/payments` for platforms with Require HMAC on. It is not deprecated. For parent platforms, a shared `apiKey` plus `setBrand` replaces the deprecated per-client HMAC flow.
- `setBrand(name, logoUrl, logoLightUrl)` is the method and `brand` is the wire field. `setChildPlatform` and `Payment.childPlatform` stay only as `@Deprecated` (warning level) aliases; `setChildPlatform` delegates to `setBrand`. Never send `child_platform`. Internal code reads the effective brand through `Payment.effectiveBrand()` (`brand`, else `childPlatform`), and parsing accepts `brand` or `child_platform` (brand wins) and mirrors it into both properties.
- The default OkHttp client uses a 30s call/connect/read/write timeout. CI gates Sources line coverage at ≥80% via Kover.

## Commands

```bash
./gradlew test
./gradlew test koverVerify
BRANTA_RUN_INTEGRATION=1 BRANTA_API_KEY=… ./gradlew test --tests pro.branta.MultiRailRoundTripTest
```

Live write/lookup tests (`ExampleQrCodesTest`, `LiveStagingCombosTest`, `MultiRailRoundTripTest`) run only when `BRANTA_RUN_INTEGRATION=1` (and `BRANTA_API_KEY` for POST).

Release: bump `version` in `build.gradle.kts`, add a `CHANGELOG.md` entry, run `./gradlew publishToMavenCentral --no-configuration-cache` (GPG signing and Maven Central credentials come from the maintainer's `~/.gradle/gradle.properties`, never the repo), click Publish in the Central Portal, then tag the release commit `vX.Y.Z` and push the tag. Do not publish until asked.

## Layout

Public API lives in `pro.branta`, `pro.branta.enums`, `pro.branta.models`, and `pro.branta.exceptions`: `BrantaService`, `PaymentBuilder`, `BrantaApiOptions`, `BrantaEnvironment`, `DestinationType`, `VerifyStatus`, `VerifyResult`, `AddPaymentResult`, payment models, and `BrantaPaymentException`.

Do **not** make public: `BrantaClient` / `HttpBrantaClient`, `QrParser`, `AesEncryption`, the EVM rail table (`EvmTokenRails`, `evmTokenType`), or `isSecretKeyAddress`. Prefer `internal` for those. `PublicApiTest` locks companion/helper visibility with kotlin-reflect.
