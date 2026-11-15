# Branta Kotlin SDK

If you are implementing this SDK in a consumer project, product rules (send vs receive, `match` / `unknown` / `tampered`, parent platforms) live in the [developer docs](https://developer.branta.pro). `README.md` is the Kotlin package: install, constructor, and samples.

## Commands

```bash
./gradlew test
./gradlew test koverVerify
BRANTA_RUN_INTEGRATION=1 BRANTA_API_KEY=… ./gradlew test --tests pro.branta.MultiRailRoundTripTest
```

Live write/lookup tests (`ExampleQrCodesTest`, `PolygonRoundTripTest`, `LiveStagingCombosTest`, `MultiRailRoundTripTest`) run only when `BRANTA_RUN_INTEGRATION=1` (and `BRANTA_API_KEY` for POST).

## Layout

Public API lives in `pro.branta`, `pro.branta.enums`, `pro.branta.models`, and `pro.branta.exceptions`: `BrantaService`, `PaymentBuilder`, `BrantaApiOptions`, `BrantaEnvironment`, `DestinationType`, `VerifyStatus`, `VerifyResult`, `AddPaymentResult`, payment models, and `BrantaPaymentException`.

Do **not** make public: `BrantaClient` / `HttpBrantaClient`, `QrParser`, `AesEncryption`, rail-family lists (`SecretKeyUriFamilies`, per-family lists), or `isSecretKeyAddress`. Prefer `internal` for those. `PublicApiTest` locks companion/helper visibility with kotlin-reflect.

| File | Role |
|------|------|
| `BrantaService.kt` | Encrypt/decrypt orchestration around HTTP. |
| `BrantaClient.kt` | Raw HTTP. Timeouts map transport failures to incomplete on GET. |
| `enums/DestinationType.kt` | Destination types, verify statuses live under `enums/`; rail families are internal. |
| `BrantaApiOptions.kt` | Options; `hmacSecret` deprecated. |
| `Serialization.kt` | JSON encode/decode. |
| `QrParser.kt` | QR / URI parsing (internal). |
| `PaymentBuilder.kt` | Fluent `Payment` construction. |
| `BrantaExtensions.kt` | Hash, URI encoding, HMAC, options resolvers. |
| `AesEncryption.kt` | Crypto primitives (internal). |

## Key behaviors

- `verify` is the only lookup. A raw on-chain address (including bare `0x`) is `unknown` and does not GET. `addPayment` encrypts a copy.
- `DestinationType` is only what `addPayment` accepts: bitcoin, bolt11, ark, silent payment, and the stablecoin rails (`usdt_polygon` … `usdc_solana`, including `usdt_base`, `usdc_base`, `usdt_arbitrum`, `usdc_arbitrum`). No `usdc_ton` yet. EVM USDT/USDC verify is ERC-681: `ethereum:<contract>@<chainId>/transfer?address=<deposit>` plus `branta_id` / `branta_secret`. Chain id plus contract select one destination type (`EvmTokenRails`, internal). `uint256` is not bound. `pol:`, `eth:`, `bsc:`, `tron:`, `ton:`, and `sol:` do not verify and do not GET. `addPayment` still encrypts `usdt_tron`, `usdt_ton`, `usdt_solana`, and `usdc_solana`. Token is on `matchedDestinations[].type`.
- `verify` `match` includes `matchedDestinations` — wallets send from that list.
- `tampered` and `incomplete` are statuses. Do not throw them. Logo-domain mismatches still throw `BrantaPaymentException`. Malformed GET bodies and network/timeouts are `incomplete`.
- `hmacSecret` is deprecated — shared `apiKey` + `setChildPlatform` is the parent-platform path.
- Default OkHttp client uses a 30s call/connect/read/write timeout. CI gates Sources line coverage at ≥80% via Kover.
