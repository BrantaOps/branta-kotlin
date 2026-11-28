package pro.branta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import pro.branta.models.AddPaymentResult
import pro.branta.models.Payment
import pro.branta.models.Platform
import org.junit.jupiter.api.Test
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.enums.VerifyStatus
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.models.VerifyResult
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
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
        assertEquals("usdt_base", DestinationType.UsdtBase.wire)
        assertEquals("usdc_arbitrum", DestinationType.UsdcArbitrum.wire)
        assertEquals(18, DestinationType.entries.size)

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
    @Suppress("DEPRECATION")
    fun `brand is public and the child platform names are deprecated aliases`() {
        val payment = PaymentBuilder().setBrand("Acme", "https://l/a.png", "https://l/b.png").build()
        assertEquals(Platform("Acme", "https://l/a.png", "https://l/b.png"), payment.brand)
        assertNull(PaymentBuilder().setBrand("Acme").build().brand?.logoUrl)

        val setter = PaymentBuilder::class.declaredFunctions.first { it.name == "setChildPlatform" }
        assertEquals(KVisibility.PUBLIC, setter.visibility)
        val deprecated = setter.findAnnotation<Deprecated>()!!
        assertEquals(DeprecationLevel.WARNING, deprecated.level)
        assertEquals("Use setBrand", deprecated.message)

        val property = Payment::class.declaredMemberProperties.first { it.name == "childPlatform" }
        val propertyDeprecated = property.findAnnotation<Deprecated>()!!
        assertEquals(DeprecationLevel.WARNING, propertyDeprecated.level)
        assertEquals("Use brand", propertyDeprecated.message)
        assertEquals(KVisibility.PUBLIC, Payment::class.declaredMemberProperties.first { it.name == "brand" }.visibility)
        assertNull(Payment::class.declaredMemberProperties.first { it.name == "brand" }.findAnnotation<Deprecated>())
        assertNull(PaymentBuilder::class.declaredFunctions.first { it.name == "setBrand" }.findAnnotation<Deprecated>())
    }

    @Test
    fun `toString redacts api key, hmac secret, and payment secret`() {
        val options = BrantaApiOptions(
            environment = BrantaEnvironment.Staging,
            apiKey = "platform-key",
            hmacSecret = "parent-hmac"
        )
        val printed = options.toString()
        assertTrue(printed.contains("environment=${BrantaEnvironment.Staging}"))
        assertTrue(printed.contains("apiKey=***"))
        assertTrue(printed.contains("hmacSecret=***"))
        assertFalse(printed.contains("platform-key"))
        assertFalse(printed.contains("parent-hmac"))
        assertEquals("k", options.copy(apiKey = "k").apiKey)

        val unset = BrantaApiOptions(environment = BrantaEnvironment.Localhost)
        assertTrue(unset.toString().contains("apiKey=null"))
        assertTrue(unset.toString().contains("hmacSecret=null"))

        val fragmentKey = "uuid-secret"
        val result = AddPaymentResult(
            payment = Payment(),
            secret = fragmentKey,
            verifyUrl = "https://staging.guardrail.branta.pro/v2/verify/cipher#k-1=$fragmentKey"
        )
        val resultPrinted = result.toString()
        assertTrue(resultPrinted.contains("secret=***"))
        assertTrue(resultPrinted.contains("/v2/verify/cipher"))
        assertFalse(resultPrinted.contains(fragmentKey))
        assertEquals(fragmentKey, result.secret)
    }

    @Test
    fun `family helpers are not public`() {
        val companion = DestinationType.Companion::class
        val rails = companion.declaredMemberProperties.first { it.name == "EvmTokenRails" }
        assertEquals(KVisibility.INTERNAL, rails.visibility)
        for (name in listOf("isSecretKeyAddress", "evmTokenType")) {
            val fn = companion.declaredFunctions.first { it.name == name }
            assertEquals(KVisibility.INTERNAL, fn.visibility, name)
        }
        val removed = listOf("PolygonEvm", "EthereumEvm", "BscEvm", "Tron", "Ton", "Solana", "SecretKeyUriFamilies")
        assertTrue(companion.declaredMemberProperties.none { it.name in removed })
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
