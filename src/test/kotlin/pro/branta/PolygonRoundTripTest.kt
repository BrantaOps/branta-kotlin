package pro.branta

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.enums.VerifyStatus
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PolygonRoundTripTest {
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

    @Test
    fun `staging usdt polygon round-trip`() = runBlocking {
        val address = "0x278297528c32eCa01295B0d2135984d3442ec963"
        val swapped = "0x1111111111111111111111111111111111111111"
        val svc = service()
        val added = svc.addPayment(
            PaymentBuilder()
                .addDestination(address, DestinationType.UsdtPolygon)
                .setTtl(120)
                .build()
        )
        val encrypted = added.payment.destinations.single().value
        assertEquals(DestinationType.UsdtPolygon, added.payment.destinations.single().type)

        val match = svc.verify(polUri(address, encrypted, added.secret))
        assertEquals(VerifyStatus.Match, match.status)
        assertEquals(DestinationType.UsdtPolygon, match.payments.single().destinations.single().type)
        assertTrue(match.payments.single().destinations.single().value.equals(address, ignoreCase = true))

        assertEquals(VerifyStatus.Match, svc.verify(polUri(address.lowercase(), encrypted, added.secret)).status)
        assertEquals(VerifyStatus.Tampered, svc.verify(polUri(swapped, encrypted, added.secret)).status)
        assertEquals(VerifyStatus.Unknown, svc.verify(address).status)
        assertEquals(VerifyStatus.Unknown, svc.verify("pol:$address?value=50&currency=USD").status)
    }

    @Test
    fun `staging usdc polygon round-trip`() = runBlocking {
        val address = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"
        val swapped = "0x1111111111111111111111111111111111111111"
        val svc = service()
        val added = svc.addPayment(
            PaymentBuilder()
                .addDestination(address, DestinationType.UsdcPolygon)
                .setTtl(120)
                .build()
        )
        val encrypted = added.payment.destinations.single().value

        val match = svc.verify(polUri(address, encrypted, added.secret))
        assertEquals(VerifyStatus.Match, match.status)
        assertEquals(DestinationType.UsdcPolygon, match.matchedDestinations.single().type)
        assertEquals(VerifyStatus.Tampered, svc.verify(polUri(swapped, encrypted, added.secret)).status)
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
