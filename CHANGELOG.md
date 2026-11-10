# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [4.2.0] - 2026-11-10

### Added
- `DestinationType.UsdtPolygon` for Polygon USDT. `addPayment` encrypts the recipient with a random secret (same pattern as bitcoin). `verify` parses `pol:` URIs with `branta_id` / `branta_secret`; other query params (`value`, `currency`, `user`) are ignored for binding. A bare `0x` address is `unknown`. EVM address compare is case-insensitive.
- `DestinationType.UsdcPolygon` for Polygon USDC. Same secret-key encrypt / `pol:` verify path as `usdt_polygon`. Token is the destination type on the payment; `pol:` remains the Polygon EVM scheme for both.
- `DestinationType.PolygonEvm`, `isPolygonEvm`, and `isSecretKeyAddress`.
- `VerifyResult.matchedDestinations` on `match` — decrypted rails that bound the scan (token for `pol:` is on `type`).

### Changed
- `verify` of a `pol:` URI binds any Polygon EVM family member whose decrypted address matches (match-if-any).
- `pol:` parsing does not stamp a token `DestinationType` on the QR address. The scheme maps to the Polygon EVM family for verify; the token still comes from the matched payment destination.
- `verify` swap-checks only the secret-key rail family that matches the QR scheme, so a payment with both bitcoin and `usdt_polygon` no longer returns `tampered` for a valid single-rail QR.
- This release matches `@branta-ops/branta` / Branta.NET / branta-go / branta-swift 4.2.0.

## [4.0.0] - 2026-11-02

### Changed
- Breaking: `verify` replaces `getPaymentsByQrCode` and `getPayments`. It returns `match`, `unknown`, `tampered`, or `incomplete`. A swapped address is `tampered`. A 404, a bare address, and a bolt11 / Ark / silent-payment lookup that does not decrypt are `unknown`. 5xx, 429, and no network are `incomplete`.
- Breaking: removed `PrivacyMode`. Every destination is encrypted. `BrantaClientOptions` and `BrantaServerBaseUrl` are now `BrantaApiOptions` and `BrantaEnvironment`.
- Breaking: `Destination.id` is the public name for the wire field `zk_id`. `addPayment` encrypts a copy and leaves the caller's `Payment` unchanged. Description is stored in encrypted metadata. An unset TTL is omitted. `DestinationType` is `bitcoin_address`, `bolt11`, `ark_address`, and `silent_payment`.
- The public library no longer includes the HTTP client, QR parser, AES helpers, or `QrParseException`. Sources moved out of the `pro.branta.v2` package. HTTP paths stay `/v2/`.

## [3.2.4] - 2026-10-29

### Fixed
- `getPaymentsByQrCode` now requires every payable instruction in a QR code to match a destination decrypted from the Branta record. Previously a swapped address paired with a rewritten `branta_secret`, an added `lightning`/`ark`/`sp` leg, or a conflicting `bc=`/`tb=` address could still come back as verified. On-chain addresses in the path, `bc=` and `tb=` must now agree (otherwise `BrantaPaymentExceptionReason.Tampered`), and payments are only returned when the on-chain address and every hash-ZK leg decrypt. `bolt12`, `lno=`, `pay=`, and LNURL instructions cannot be verified and return no payments without a lookup (ported from `branta-js` #78)

## [3.2.3] - 2026-10-28

### Added
- Recognize signet and regtest Ark addresses (`tark1`) as `ark_address`, alongside mainnet `ark1`. Strict-mode hash-ZK lookup and registration treat them the same way as `ark1`.

### Fixed
- `btcPayServerPluginVersion` now serializes as `btc_pay_server_plugin_version`, the field name the API accepts. The previous name `btcpay_server_plugin_version` was ignored.

## [3.2.2] - 2026-09-30

### Fixed
- `getPaymentsByQrCode` now verifies that the plaintext Bitcoin address parsed from a scanned QR code matches the address decrypted via `branta_id`/`branta_secret`, throwing `BrantaPaymentException` with `reason = BrantaPaymentExceptionReason.Tampered` on mismatch. Closes a gap where an attacker could swap the visible address in a `bitcoin:` URI while leaving a legitimate, verified `branta_id`/`branta_secret` pair untouched (ported from `branta-js` 3.2.1)
- Fixed a same-origin validation gap: if the first payment in a `getPayments` response had no `platformLogoUrl`, validation silently stopped checking every later payment's logo URLs entirely. Logo-URL checks now run independently per payment, and now also cover `platformLogoLightUrl`, `parentPlatform.logoUrl`/`logoLightUrl`, and `childPlatform.logoUrl`/`logoLightUrl` (previously only `platformLogoUrl` was checked)

## [3.2.0] - 2026-07-11

### Added
- Initial release of the Branta Kotlin SDK for Android
- `BrantaService` with `getPaymentsByQrCode`, `getPayments`, `addPayment`, and `isApiKeyValid`
- `PaymentBuilder` fluent builder with ZK support, metadata encryption, and child platform tagging
- `QrParser` handles `bitcoin:`, `lightning:` URIs and plain-text values with full BIP-21 query string decoding
- AES-256-GCM encryption with deterministic nonce support for hash-ZK types (Bolt11, ArkAddress, SilentPayment)
- Encrypted metadata DEK envelope support
- `PrivacyMode.Strict` and `PrivacyMode.Loose` enforcement
- HMAC-SHA256 request signing for parent platform flows
- `ChildPlatform` support via `PaymentBuilder.setChildPlatform()`
- `ParentPlatform` on `Payment` response model
- Silent payment (`sp1`/`tsp1`) detection and ZK support
- Maven Central publishing via `com.vanniktech.maven.publish`
- Full unit test coverage with JUnit 5 + MockK
- Integration tests against staging and production environments
