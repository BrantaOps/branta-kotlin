# Branta Kotlin SDK

If you are implementing this SDK in a consumer project, product rules (send vs receive, `match` / `unknown` / `tampered`, parent platforms) live in the [developer docs](https://developer.branta.pro). `README.md` is the Kotlin package: install, constructor, and samples.

## Commands

```bash
./gradlew test
./gradlew build
```

Live example-QR lookups run only when `BRANTA_RUN_INTEGRATION=1`.

## Layout

Public API is `BrantaService`, `PaymentBuilder`, `BrantaApiOptions`, `BrantaEnvironment`, `DestinationType`, `VerifyResult`, and the payment models in `pro.branta`.

- `BrantaService` orchestrates encrypt/decrypt around HTTP. `BrantaClient` is internal.
- `verify` is the only lookup. A raw on-chain address is `unknown` and does not GET. `addPayment` encrypts a copy.
- `DestinationType` is only what `addPayment` accepts: `bitcoin_address`, `bolt11`, `ark_address`, `silent_payment`.
- `tampered` and `incomplete` are statuses. Do not throw them. Logo-domain mismatches still throw `BrantaPaymentException`.
