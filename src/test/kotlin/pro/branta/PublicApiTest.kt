package pro.branta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.enums.VerifyStatus
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.models.VerifyResult
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties

/**
 * Consumer-surface lock. Family helpers and HTTP/crypto internals stay internal;
 * this test fails if they regain public visibility.
 */
class PublicApiTest {
    @Test
    fun `consumer types are reachable`() {
        assertEquals("usdt_ethereum", DestinationType.UsdtEthereum.wire)
        assertEquals("usdc_solana", DestinationType.UsdcSolana.wire)
        assertEquals("usdt_ton", DestinationType.UsdtTon.wire)
        assertNull(DestinationType.fromWire("usdc_ton"))
        assertEquals(14, DestinationType.entries.size)

        assertEquals(VerifyStatus.Match, VerifyStatus.Match)
        assertEquals("https://staging.guardrail.branta.pro", BrantaEnvironment.Staging.url)

        val options = BrantaApiOptions(environment = BrantaEnvironment.Localhost, apiKey = "k")
        assertEquals("k", options.apiKey)

        val service = BrantaService(environment = BrantaEnvironment.Staging)
        assertNotNull(service)

        val payment = PaymentBuilder()
            .addDestination("bc1qexample", DestinationType.BitcoinAddress)
            .build()
        assertEquals(1, payment.destinations.size)

        val verify = VerifyResult(VerifyStatus.Unknown, "https://example/v2/verify/x")
        assertEquals(VerifyStatus.Unknown, verify.status)
        assertTrue(verify.matchedDestinations.isEmpty())

        val error = BrantaPaymentException("platformLogoUrl domain does not match the configured API domain")
        assertTrue(error.message!!.contains("platformLogoUrl"))
    }

    @Test
    fun `family helpers are not public`() {
        val companion = DestinationType.Companion::class
        val internalProps = listOf(
            "PolygonEvm", "EthereumEvm", "BscEvm", "Tron", "Ton", "Solana", "SecretKeyUriFamilies"
        )
        for (name in internalProps) {
            val prop = companion.declaredMemberProperties.first { it.name == name }
            assertEquals(KVisibility.INTERNAL, prop.visibility, name)
        }
        val secretKey = companion.declaredFunctions.first { it.name == "isSecretKeyAddress" }
        assertEquals(KVisibility.INTERNAL, secretKey.visibility)
        assertTrue(companion.declaredFunctions.none { it.name == "isPolygonEvm" })
    }

    @Test
    fun `http client and qr parser stay internal`() {
        assertEquals(
            KVisibility.INTERNAL,
            BrantaClient::class.visibility
        )
        // Interface may be null visibility on JVM; check concrete type.
        assertEquals(KVisibility.INTERNAL, HttpBrantaClient::class.visibility)
        assertEquals(KVisibility.INTERNAL, QrParser::class.visibility)
        assertEquals(KVisibility.INTERNAL, AesEncryption::class.visibility)
    }
}
