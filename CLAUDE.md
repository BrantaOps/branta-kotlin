# Branta Kotlin SDK

If you are implementing this SDK in a consumer project, see the **Integration Guide** section at the top of `README.md`.

---

## Developer notes (SDK contributors)

### Project layout

- `src/main/kotlin/pro/branta/` — top-level: `BrantaClientOptions` plus `enums/` and `exceptions/`
- `src/main/kotlin/pro/branta/v2/` — V2 API surface: services, builder, parser, crypto, extensions
- `src/main/kotlin/pro/branta/v2/interfaces/` — `IBrantaService`, `IBrantaClient`, `IAesEncryption`, `ISecretGenerator`
- `src/main/kotlin/pro/branta/v2/models/` — `Payment`, `Destination`, `PaymentsResult`, `Platform`, `AddPaymentResult`
- `src/test/kotlin/pro/branta/` — unit tests mirroring the main layout

### Build / test

```bash
./gradlew build        # compile + test
./gradlew test         # tests only
./gradlew publishToMavenLocal   # local Maven install for testing in a consumer project
```

**Note:** `gradle/wrapper/gradle-wrapper.jar` is not committed (binary file). If you do not have the wrapper, run once with Gradle installed:
```bash
gradle wrapper --gradle-version 8.10.2
```

### Version file

Version is in `build.gradle.kts` (`version = "X.Y.Z"`). Update here for releases.

### Key behaviors to preserve

- **`PrivacyMode.Strict` is the default.** `getPayments` throws `BrantaPaymentException` for plain-text on-chain lookups; `getPaymentsByQrCode` returns an empty `PaymentsResult` with a populated `verifyUrl`. `addPayment` rejects any non-ZK destination.
- **`verifyUrl` is always returned**, including on a miss.
- **ZK modes.** Bitcoin addresses use a random GUID secret (from `ISecretGenerator`). Bolt11, ArkAddress, and SilentPayment use a deterministic key derived from `SHA256(lowercase(value))`.
- **Metadata encryption.** When `Payment.metadata` is set and at least one ZK destination exists, a DEK is generated, metadata is encrypted with the DEK, and each ZK destination stores its own `encryptedDek = AES(DEK, destinationKey)`.
- **Decryption failures are silently swallowed.** Wrong key leaves `isEncrypted = true`; failed DEK decryption leaves `metadata` as-is and `isMetadataDecrypted = false`.
- **Logo URL domain verification.** After every GET, `BrantaClient` verifies that `platformLogoUrl` matches the configured base URL's origin. Mismatch throws `BrantaPaymentException`.

### Conventions

- Package root: `pro.branta`; V2 API under `pro.branta.v2`
- Interfaces use `I` prefix: `IBrantaService`, `IBrantaClient`, `IAesEncryption`, `ISecretGenerator`
- Enum values: `PascalCase` (`PrivacyMode.Strict`, `DestinationType.BitcoinAddress`)
- Async: `suspend` functions — no `Async` suffix, no `CancellationToken` parameter (coroutine scope handles it)
- No DI framework: `BrantaService` constructor accepts all dependencies with defaults for consumer use; inject mocks for tests
- Tests: JUnit 5 + MockK; `coEvery/coVerify` for suspend functions; `runTest` from `kotlinx-coroutines-test`
- Keep parity with `branta-dotnet`, `branta-js`, `branta-dart`, and `branta-python`

### Publishing

See `README.md` → Publishing section. Requires a GPG key and Maven Central credentials in `~/.gradle/gradle.properties`.
