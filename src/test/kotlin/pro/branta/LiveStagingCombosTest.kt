package pro.branta

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.enums.VerifyStatus
import pro.branta.models.AddPaymentResult
import pro.branta.models.Payment
import pro.branta.models.VerifyResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Extra live staging POST→GET combinations beyond the dedicated polygon round-trips. */
class LiveStagingCombosTest {
    private fun service(): BrantaService {
        val token = System.getenv("BRANTA_INTEGRATION_METRICS_TOKEN")
        val httpClient = if (token.isNullOrEmpty()) {
            OkHttpClient()
        } else {
            OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("X-Branta-Integration-Token", token).build())
            }.build()
        }
        return BrantaService(
            environment = BrantaEnvironment.Staging,
            apiKey = System.getenv("BRANTA_API_KEY"),
            httpClient = httpClient
        )
    }

    private fun polUri(recipient: String, encrypted: String, secret: String): String {
        val q = listOf(
            "value" to "50",
            "user" to "send",
            "currency" to "USD",
            "branta_id" to encrypted,
            "branta_secret" to secret
        ).joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }
        return "pol:$recipient?$q"
    }

    private fun urlQueryEscape(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    @Test
    fun `staging live combos`() = runBlocking {
        val svc = service()
        val btc = "bc1qgw3dzmhnyvcswc9r0v0z0ajtp8ulm4nuyeahwr"
        val usdt = "0x278297528c32eCa01295B0d2135984d3442ec963"
        val usdc = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"
        val swap = "0x1111111111111111111111111111111111111111"
        val btcSw = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"

        suspend fun mustAdd(payment: Payment): AddPaymentResult = svc.addPayment(payment)

        suspend fun mustStatus(qr: String, want: VerifyStatus): VerifyResult {
            val result = svc.verify(qr)
            assertEquals(want, result.status, qr)
            return result
        }

        run {
            val added = mustAdd(
                PaymentBuilder().addDestination(btc, DestinationType.BitcoinAddress).setTtl(120).build()
            )
            val enc = added.payment.destinations.single().value
            val qr = "bitcoin:$btc?branta_id=${urlQueryEscape(enc)}&branta_secret=${urlQueryEscape(added.secret)}"
            val match = mustStatus(qr, VerifyStatus.Match)
            assertEquals(1, match.matchedDestinations.size)
            assertEquals(DestinationType.BitcoinAddress, match.matchedDestinations.single().type)
            mustStatus(
                "bitcoin:$btcSw?branta_id=${urlQueryEscape(enc)}&branta_secret=${urlQueryEscape(added.secret)}",
                VerifyStatus.Tampered
            )
            mustStatus("bitcoin:$btc", VerifyStatus.Unknown)
        }

        run {
            val added = mustAdd(
                PaymentBuilder()
                    .addDestination(usdt, DestinationType.UsdtPolygon)
                    .addDestination(usdc, DestinationType.UsdcPolygon)
                    .setTtl(120)
                    .build()
            )
            var encUsdt = ""
            var encUsdc = ""
            for (d in added.payment.destinations) {
                when (d.type) {
                    DestinationType.UsdtPolygon -> encUsdt = d.value
                    DestinationType.UsdcPolygon -> encUsdc = d.value
                    else -> Unit
                }
            }
            val usdtMatch = mustStatus(polUri(usdt, encUsdt, added.secret), VerifyStatus.Match)
            assertEquals(1, usdtMatch.matchedDestinations.size)
            assertEquals(DestinationType.UsdtPolygon, usdtMatch.matchedDestinations.single().type)
            val usdcMatch = mustStatus(polUri(usdc, encUsdc, added.secret), VerifyStatus.Match)
            assertEquals(1, usdcMatch.matchedDestinations.size)
            assertEquals(DestinationType.UsdcPolygon, usdcMatch.matchedDestinations.single().type)
        }

        run {
            val added = mustAdd(
                PaymentBuilder()
                    .addDestination(usdt, DestinationType.UsdtPolygon)
                    .addDestination(usdt, DestinationType.UsdcPolygon)
                    .setTtl(120)
                    .build()
            )
            val enc = added.payment.destinations.first().value
            val match = mustStatus(polUri(usdt, enc, added.secret), VerifyStatus.Match)
            assertEquals(2, match.matchedDestinations.size)
        }

        run {
            val added = mustAdd(
                PaymentBuilder()
                    .addDestination(btc, DestinationType.BitcoinAddress)
                    .addDestination(usdt, DestinationType.UsdtPolygon)
                    .setTtl(120)
                    .build()
            )
            var encBtc = ""
            var encUsdt = ""
            for (d in added.payment.destinations) {
                when (d.type) {
                    DestinationType.BitcoinAddress -> encBtc = d.value
                    DestinationType.UsdtPolygon -> encUsdt = d.value
                    else -> Unit
                }
            }
            val btcMatch = mustStatus(
                "bitcoin:$btc?branta_id=${urlQueryEscape(encBtc)}&branta_secret=${urlQueryEscape(added.secret)}",
                VerifyStatus.Match
            )
            assertEquals(1, btcMatch.matchedDestinations.size)
            assertEquals(DestinationType.BitcoinAddress, btcMatch.matchedDestinations.single().type)
            val polMatch = mustStatus(polUri(usdt, encUsdt, added.secret), VerifyStatus.Match)
            assertEquals(1, polMatch.matchedDestinations.size)
            assertEquals(DestinationType.UsdtPolygon, polMatch.matchedDestinations.single().type)
            mustStatus(polUri(swap, encUsdt, added.secret), VerifyStatus.Tampered)
        }

        run {
            val added = mustAdd(
                PaymentBuilder().addDestination(usdt, DestinationType.UsdtPolygon).setTtl(120).build()
            )
            val enc = added.payment.destinations.single().value
            mustStatus(polUri(usdt.lowercase(), enc, added.secret), VerifyStatus.Match)
            mustStatus(polUri(usdt, enc, "00000000-0000-0000-0000-000000000000"), VerifyStatus.Unknown)
            mustStatus(usdt, VerifyStatus.Unknown)
            mustStatus("pol:$usdt?value=50&currency=USD", VerifyStatus.Unknown)
        }
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
