package pro.branta

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.enums.VerifyStatus
import pro.branta.models.Payment
import pro.branta.models.VerifyResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Extra live staging POST→GET combinations. Skipped unless BRANTA_RUN_INTEGRATION=1 and BRANTA_API_KEY is set. */
class LiveStagingCombosTest {
    private fun service(): BrantaService {
        val token = System.getenv("BRANTA_INTEGRATION_METRICS_TOKEN")
        val httpClient = if (token.isNullOrEmpty()) {
            defaultHttpClient()
        } else {
            defaultHttpClient().newBuilder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("X-Branta-Integration-Token", token).build())
            }.build()
        }
        return BrantaService(
            environment = BrantaEnvironment.Staging,
            apiKey = System.getenv("BRANTA_API_KEY"),
            httpClient = httpClient
        )
    }

    private fun rail(type: DestinationType): DestinationType.EvmTokenRail =
        DestinationType.EvmTokenRails.first { it.type == type }

    private fun urlQueryEscape(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    /** ERC-681 transfer URI for one EVM rail, as a wallet would show it. */
    private fun transferUri(type: DestinationType, recipient: String, encrypted: String, secret: String): String {
        val rail = rail(type)
        return "ethereum:${rail.contract}@${rail.chainId}/transfer?address=$recipient&uint256=1000000" +
            "&branta_id=${urlQueryEscape(encrypted)}&branta_secret=${urlQueryEscape(secret)}"
    }

    private fun bitcoinUri(address: String, encrypted: String, secret: String): String =
        "bitcoin:$address?branta_id=${urlQueryEscape(encrypted)}&branta_secret=${urlQueryEscape(secret)}"

    private val btc = "bc1qgw3dzmhnyvcswc9r0v0z0ajtp8ulm4nuyeahwr"
    private val usdt = "0x278297528c32eCa01295B0d2135984d3442ec963"
    private val usdc = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"
    private val swap = "0x1111111111111111111111111111111111111111"
    private val btcSw = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"

    private suspend fun BrantaService.mustStatus(qr: String, want: VerifyStatus): VerifyResult {
        val result = verify(qr)
        assertEquals(want, result.status, qr)
        return result
    }

    private fun Payment.encryptedOf(type: DestinationType): String = destinations.first { it.type == type }.value

    @Test
    fun `bitcoin round trip match and tamper`() = runBlocking {
        val svc = service()
        val added = svc.addPayment(PaymentBuilder().addDestination(btc, DestinationType.BitcoinAddress).setTtl(120).build())
        val enc = added.payment.destinations.single().value
        val match = svc.mustStatus(bitcoinUri(btc, enc, added.secret), VerifyStatus.Match)
        assertEquals(listOf(DestinationType.BitcoinAddress), match.matchedDestinations.map { it.type })
        svc.mustStatus(bitcoinUri(btcSw, enc, added.secret), VerifyStatus.Tampered)
        svc.mustStatus("bitcoin:$btc", VerifyStatus.Unknown)
        Unit
    }

    @Test
    fun `usdt and usdc with different addresses bind their own rail`() = runBlocking {
        val svc = service()
        val added = svc.addPayment(
            PaymentBuilder()
                .addDestination(usdt, DestinationType.UsdtPolygon)
                .addDestination(usdc, DestinationType.UsdcPolygon)
                .setTtl(120)
                .build()
        )
        val encUsdt = added.payment.encryptedOf(DestinationType.UsdtPolygon)
        val encUsdc = added.payment.encryptedOf(DestinationType.UsdcPolygon)
        val usdtMatch = svc.mustStatus(transferUri(DestinationType.UsdtPolygon, usdt, encUsdt, added.secret), VerifyStatus.Match)
        assertEquals(listOf(DestinationType.UsdtPolygon), usdtMatch.matchedDestinations.map { it.type })
        val usdcMatch = svc.mustStatus(transferUri(DestinationType.UsdcPolygon, usdc, encUsdc, added.secret), VerifyStatus.Match)
        assertEquals(listOf(DestinationType.UsdcPolygon), usdcMatch.matchedDestinations.map { it.type })
        Unit
    }

    @Test
    fun `the same address under two tokens binds only the scanned token`() = runBlocking {
        val svc = service()
        val added = svc.addPayment(
            PaymentBuilder()
                .addDestination(usdt, DestinationType.UsdtPolygon)
                .addDestination(usdt, DestinationType.UsdcPolygon)
                .setTtl(120)
                .build()
        )
        val encUsdt = added.payment.encryptedOf(DestinationType.UsdtPolygon)
        val encUsdc = added.payment.encryptedOf(DestinationType.UsdcPolygon)
        val usdtMatch = svc.mustStatus(transferUri(DestinationType.UsdtPolygon, usdt, encUsdt, added.secret), VerifyStatus.Match)
        assertEquals(listOf(DestinationType.UsdtPolygon), usdtMatch.matchedDestinations.map { it.type })
        val usdcMatch = svc.mustStatus(transferUri(DestinationType.UsdcPolygon, usdt, encUsdc, added.secret), VerifyStatus.Match)
        assertEquals(listOf(DestinationType.UsdcPolygon), usdcMatch.matchedDestinations.map { it.type })
        svc.mustStatus(transferUri(DestinationType.UsdtPolygon, swap, encUsdt, added.secret), VerifyStatus.Tampered)
        Unit
    }

    @Test
    fun `bitcoin and usdt sibling rails isolate by type`() = runBlocking {
        val svc = service()
        val added = svc.addPayment(
            PaymentBuilder()
                .addDestination(btc, DestinationType.BitcoinAddress)
                .addDestination(usdt, DestinationType.UsdtPolygon)
                .setTtl(120)
                .build()
        )
        val encBtc = added.payment.encryptedOf(DestinationType.BitcoinAddress)
        val encUsdt = added.payment.encryptedOf(DestinationType.UsdtPolygon)

        val btcMatch = svc.mustStatus(bitcoinUri(btc, encBtc, added.secret), VerifyStatus.Match)
        assertEquals(listOf(DestinationType.BitcoinAddress), btcMatch.matchedDestinations.map { it.type })
        assertTrue(btcMatch.payments.single().destinations.first { it.type == DestinationType.UsdtPolygon }.isEncrypted)

        val usdtMatch = svc.mustStatus(transferUri(DestinationType.UsdtPolygon, usdt, encUsdt, added.secret), VerifyStatus.Match)
        assertEquals(listOf(DestinationType.UsdtPolygon), usdtMatch.matchedDestinations.map { it.type })
        assertTrue(usdtMatch.payments.single().destinations.first { it.type == DestinationType.BitcoinAddress }.isEncrypted)

        svc.mustStatus(transferUri(DestinationType.UsdtPolygon, swap, encUsdt, added.secret), VerifyStatus.Tampered)
        Unit
    }

    @Test
    fun `usdt case-insensitive wrong secret and removed scheme are handled`() = runBlocking {
        val svc = service()
        val added = svc.addPayment(PaymentBuilder().addDestination(usdt, DestinationType.UsdtPolygon).setTtl(120).build())
        val enc = added.payment.destinations.single().value
        svc.mustStatus(transferUri(DestinationType.UsdtPolygon, usdt.lowercase(), enc, added.secret), VerifyStatus.Match)
        svc.mustStatus(
            transferUri(DestinationType.UsdtPolygon, usdt, enc, "00000000-0000-0000-0000-000000000000"),
            VerifyStatus.Unknown
        )
        svc.mustStatus(usdt, VerifyStatus.Unknown)
        // The old pol: scheme no longer verifies, even with valid Branta params.
        svc.mustStatus(
            "pol:$usdt?value=50&branta_id=${urlQueryEscape(enc)}&branta_secret=${urlQueryEscape(added.secret)}",
            VerifyStatus.Unknown
        )
        Unit
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun skipUnlessOptIn() {
            assumeTrue(System.getenv("BRANTA_RUN_INTEGRATION") == "1", "Live lookups skipped unless BRANTA_RUN_INTEGRATION=1")
            assumeTrue(!System.getenv("BRANTA_API_KEY").isNullOrEmpty(), "Live POST skipped unless BRANTA_API_KEY is set")
        }
    }
}
