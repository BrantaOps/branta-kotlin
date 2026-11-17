package pro.branta

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.enums.VerifyStatus
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.stream.Stream

/** Live staging addPayment → verify for every rail. Skipped unless BRANTA_RUN_INTEGRATION=1 and BRANTA_API_KEY is set. */
class MultiRailRoundTripTest {
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

    /** Mixed-case deposit per rail, so the lowercase-scan check differs from the stored text. */
    private fun depositFor(type: DestinationType): String {
        val index = DestinationType.EvmTokenRails.indexOfFirst { it.type == type } + 1
        val hex = index.toString(16)
        return "0xAb" + "1".repeat(38 - hex.length) + hex
    }

    @Test
    fun `bitcoin round trip`() = runBlocking {
        val svc = service()
        val added = svc.addPayment(
            PaymentBuilder().addDestination(BITCOIN, DestinationType.BitcoinAddress).setTtl(180).build()
        )
        val encrypted = added.payment.destinations.single().value
        assertNotEquals(BITCOIN, encrypted)

        val match = svc.verify(bitcoinUri(BITCOIN, encrypted, added.secret))
        assertEquals(VerifyStatus.Match, match.status)
        assertEquals(listOf(DestinationType.BitcoinAddress), match.matchedDestinations.map { it.type })
        assertEquals(BITCOIN, match.matchedDestinations.single().value)

        assertEquals(VerifyStatus.Tampered, svc.verify(bitcoinUri(BITCOIN_SWAPPED, encrypted, added.secret)).status)
        assertEquals(VerifyStatus.Unknown, svc.verify(BITCOIN).status)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("evmTypes")
    fun `evm rail round trips through erc681`(type: DestinationType) = runBlocking {
        val rail = rail(type)
        val address = depositFor(type)
        val svc = service()
        val added = svc.addPayment(PaymentBuilder().addDestination(address, type).setTtl(180).build())
        val encrypted = added.payment.destinations.single().value
        assertEquals(type, added.payment.destinations.single().type)
        assertNotEquals(address, encrypted)

        val match = svc.verify(transferUri(type, address, encrypted, added.secret))
        assertEquals(VerifyStatus.Match, match.status)
        assertEquals(listOf(type), match.matchedDestinations.map { it.type })
        assertEquals(address, match.matchedDestinations.single().value)

        val folded = svc.verify(transferUri(type, address.lowercase(), encrypted, added.secret))
        assertEquals(VerifyStatus.Match, folded.status)

        val tampered = svc.verify(transferUri(type, SWAPPED_EVM, encrypted, added.secret))
        assertEquals(VerifyStatus.Tampered, tampered.status)

        assertEquals(VerifyStatus.Unknown, svc.verify(address).status)
        val noBranta = svc.verify(
            "ethereum:${rail.contract}@${rail.chainId}/transfer?address=$address&uint256=1000000"
        )
        assertEquals(VerifyStatus.Unknown, noBranta.status)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("postOnlyRails")
    fun `tron ton and solana still encrypt on addPayment`(type: DestinationType, address: String) = runBlocking {
        val svc = service()
        val added = svc.addPayment(PaymentBuilder().addDestination(address, type).setTtl(180).build())
        assertEquals(type, added.payment.destinations.single().type)
        assertNotEquals(address, added.payment.destinations.single().value)
        // No verify URI exists for these rails, so a bare address stays unknown.
        assertEquals(VerifyStatus.Unknown, svc.verify(address).status)
    }

    @Test
    fun `an ethereum scan does not bind a polygon-only payment`() = runBlocking {
        val address = depositFor(DestinationType.UsdtPolygon)
        val svc = service()
        val added = svc.addPayment(
            PaymentBuilder().addDestination(address, DestinationType.UsdtPolygon).setTtl(180).build()
        )
        val encrypted = added.payment.destinations.single().value

        val cross = svc.verify(transferUri(DestinationType.UsdtEthereum, address, encrypted, added.secret))
        assertEquals(VerifyStatus.Unknown, cross.status)
        val correct = svc.verify(transferUri(DestinationType.UsdtPolygon, address, encrypted, added.secret))
        assertEquals(VerifyStatus.Match, correct.status)
    }

    @Test
    fun `a dual-token payment binds only the scanned token`() = runBlocking {
        val usdt = depositFor(DestinationType.UsdtEthereum)
        val usdc = depositFor(DestinationType.UsdcEthereum)
        val svc = service()
        val added = svc.addPayment(
            PaymentBuilder()
                .addDestination(usdt, DestinationType.UsdtEthereum)
                .addDestination(usdc, DestinationType.UsdcEthereum)
                .setTtl(180)
                .build()
        )
        val encryptedUsdt = added.payment.destinations.first { it.type == DestinationType.UsdtEthereum }.value

        val matchUsdt = svc.verify(transferUri(DestinationType.UsdtEthereum, usdt, encryptedUsdt, added.secret))
        assertEquals(VerifyStatus.Match, matchUsdt.status)
        assertEquals(listOf(DestinationType.UsdtEthereum), matchUsdt.matchedDestinations.map { it.type })
        assertTrue(matchUsdt.payments.single().destinations.first { it.type == DestinationType.UsdcEthereum }.isEncrypted)

        val swapped = svc.verify(transferUri(DestinationType.UsdtEthereum, SWAPPED_EVM, encryptedUsdt, added.secret))
        assertEquals(VerifyStatus.Tampered, swapped.status)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun skipUnlessOptIn() {
            assumeTrue(System.getenv("BRANTA_RUN_INTEGRATION") == "1", "Live lookups skipped unless BRANTA_RUN_INTEGRATION=1")
            assumeTrue(!System.getenv("BRANTA_API_KEY").isNullOrEmpty(), "Live POST skipped unless BRANTA_API_KEY is set")
        }

        private const val BITCOIN = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"
        private const val BITCOIN_SWAPPED = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
        private val SWAPPED_EVM = "0x" + "9".repeat(40)

        @JvmStatic
        fun evmTypes(): Stream<DestinationType> = DestinationType.EvmTokenRails.map { it.type }.stream()

        @JvmStatic
        fun postOnlyRails(): Stream<Arguments> = Stream.of(
            Arguments.of(DestinationType.UsdtTron, "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"),
            Arguments.of(DestinationType.UsdtTon, "EQDtFpEwcFAEcRe5mLVh2N6C0y-_tur3BYOM93pEZPw390by"),
            Arguments.of(DestinationType.UsdtSolana, "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"),
            Arguments.of(DestinationType.UsdcSolana, "7EqQdEULxWcraVx3mXKFjc84LhCkMGZCkRuDofycyinf")
        )
    }
}
