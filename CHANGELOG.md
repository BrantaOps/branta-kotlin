# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
