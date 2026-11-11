# Branta Kotlin SDK

If you are implementing this SDK in a consumer project, product rules (send vs receive, `match` / `unknown` / `tampered`, parent platforms) live in the [developer docs](https://developer.branta.pro). `README.md` is the Kotlin package: install, constructor, and samples.

## Commands

```bash
./gradlew test
./gradlew build
```

Live example-QR lookups, polygon round-trips, and staging combo POSTs run only when `BRANTA_RUN_INTEGRATION=1` (API key required for write tests).

## Layout

Public API is `BrantaService`, `PaymentBuilder`, `BrantaApiOptions`, `BrantaEnvironment`, `DestinationType`, `VerifyResult`, and the payment models in `pro.branta`.

- `BrantaService` orchestrates encrypt/decrypt around HTTP. `HttpBrantaClient` is internal.
- `verify` is the only lookup. A raw on-chain address (including bare `0x`) is `unknown` and does not GET. `addPayment` encrypts a copy.
- `DestinationType` is only what `addPayment` accepts: `bitcoin_address`, `bolt11`, `ark_address`, `silent_payment`, `usdt_polygon`, `usdc_polygon`, `usdt_ethereum`, `usdc_ethereum`, `usdt_bsc`, `usdc_bsc`, `usdt_tron`, `usdt_ton`, `usdt_solana`, `usdc_solana`. Pass stablecoin types explicitly.
- Chain URI verify (`pol:`, `eth:`, `bsc:`, `tron:`, `ton:`, `sol:`; see `DestinationType.SecretKeyUriFamilies`) is match-if-any within the scheme's family; token is on `matchedDestinations[].type`, not the scheme. A bare address is `unknown`.
- `tampered` and `incomplete` are statuses. Do not throw them. Logo-domain mismatches still throw `BrantaPaymentException`.
