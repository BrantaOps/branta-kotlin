package pro.branta

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.enums.VerifyStatus
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.stream.Stream

/** Live staging addPayment → verify for every secret-key rail. Gated like PolygonRoundTripTest. */
class MultiRailRoundTripTest {
    data class SecretKeyRail(
        val type: DestinationType,
        val scheme: String,
        val address: String,
        val swapped: String,
        val caseFold: Boolean = false
    )

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

    private fun secretKeyUri(scheme: String, recipient: String, encrypted: String, secret: String): String {
        val q = listOf(
            "branta_id" to encrypted,
            "branta_secret" to secret
        ).joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
        }
        return "$scheme:$recipient?$q"
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("secretKeyRails")
    fun `staging secret-key round-trip`(rail: SecretKeyRail) = runBlocking {
        val svc = service()
        val added = svc.addPayment(
            PaymentBuilder()
                .addDestination(rail.address, rail.type)
                .setTtl(120)
                .build()
        )
        val encrypted = added.payment.destinations.single().value
        assertEquals(rail.type, added.payment.destinations.single().type)

        val match = svc.verify(secretKeyUri(rail.scheme, rail.address, encrypted, added.secret))
        assertEquals(VerifyStatus.Match, match.status)
        assertEquals(rail.type, match.matchedDestinations.single().type)
        if (rail.caseFold) {
            assertTrue(match.matchedDestinations.single().value.equals(rail.address, ignoreCase = true))
            assertEquals(
                VerifyStatus.Match,
                svc.verify(secretKeyUri(rail.scheme, rail.address.lowercase(), encrypted, added.secret)).status
            )
        } else {
            assertEquals(rail.address, match.matchedDestinations.single().value)
        }
        assertEquals(
            VerifyStatus.Tampered,
            svc.verify(secretKeyUri(rail.scheme, rail.swapped, encrypted, added.secret)).status
        )
        assertEquals(VerifyStatus.Unknown, svc.verify(rail.address).status)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun skipUnlessOptIn() {
            assumeTrue(System.getenv("BRANTA_RUN_INTEGRATION") == "1", "Live lookups skipped unless BRANTA_RUN_INTEGRATION=1")
            assumeTrue(!System.getenv("BRANTA_API_KEY").isNullOrEmpty(), "Live POST skipped unless BRANTA_API_KEY is set")
        }

        @JvmStatic
        fun secretKeyRails(): Stream<SecretKeyRail> = Stream.of(
            SecretKeyRail(
                DestinationType.BitcoinAddress,
                "bitcoin",
                "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
            ),
            SecretKeyRail(
                DestinationType.UsdtPolygon,
                "pol",
                "0x278297528c32eCa01295B0d2135984d3442ec963",
                "0x1111111111111111111111111111111111111111",
                caseFold = true
            ),
            SecretKeyRail(
                DestinationType.UsdcPolygon,
                "pol",
                "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B",
                "0x2222222222222222222222222222222222222222",
                caseFold = true
            ),
            SecretKeyRail(
                DestinationType.UsdtEthereum,
                "eth",
                "0xBF5550253939187432Ef4dccE8089765d3DCb47f",
                "0x3333333333333333333333333333333333333333",
                caseFold = true
            ),
            SecretKeyRail(
                DestinationType.UsdcEthereum,
                "eth",
                "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                "0x4444444444444444444444444444444444444444",
                caseFold = true
            ),
            SecretKeyRail(
                DestinationType.UsdtBsc,
                "bsc",
                "0x55d398326f99059fF775485246999027B3197955",
                "0x5555555555555555555555555555555555555555",
                caseFold = true
            ),
            SecretKeyRail(
                DestinationType.UsdcBsc,
                "bsc",
                "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d",
                "0x6666666666666666666666666666666666666666",
                caseFold = true
            ),
            SecretKeyRail(
                DestinationType.UsdtTron,
                "tron",
                "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb",
                "TXyZ1111111111111111111111111111111"
            ),
            SecretKeyRail(
                DestinationType.UsdtTon,
                "ton",
                "EQDtFpEwcFAEcRe5mLVh2N6C0y-_tur3BYOM93pEZPw390by",
                "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c"
            ),
            SecretKeyRail(
                DestinationType.UsdtSolana,
                "sol",
                "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
                "11111111111111111111111111111111"
            ),
            SecretKeyRail(
                DestinationType.UsdcSolana,
                "sol",
                "7EqQdEULxWcraVx3mXKFjc84LhCkMGZCkRuDofycyinf",
                "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
            )
        )
    }
}
