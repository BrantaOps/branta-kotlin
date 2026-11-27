package pro.branta

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.models.Payment
import pro.branta.models.Platform
import kotlin.test.assertFailsWith

@Suppress("DEPRECATION")
class BrandTest {
    private val address = "bc1qmerchant"

    private class FakeClient : BrantaClient {
        var posted: Payment? = null
        override suspend fun getPayments(destinationValue: String, options: BrantaApiOptions?): List<Payment> =
            emptyList()
        override suspend fun postPayment(payment: Payment, options: BrantaApiOptions?): Payment {
            posted = payment
            return payment
        }
        override suspend fun isApiKeyValid(options: BrantaApiOptions?): Boolean = true
    }

    private class FixedSecret(vararg values: String) : SecretGenerator {
        private val values = ArrayDeque(values.toList())
        override fun generate(): String = values.removeFirst()
        override val deterministicNonce: Boolean = false
    }

    private fun wire(payment: Payment): JsonObject =
        Json.parseToJsonElement(paymentToJson(payment)).jsonObject

    private fun obj(payment: Payment, key: String): JsonObject = wire(payment)[key]!!.jsonObject

    @Test
    fun `setBrand writes brand and nothing else`() {
        val payment = PaymentBuilder().addDestination(address).setBrand("Acme", "https://l/a.png", "https://l/b.png").build()
        assertEquals(Platform("Acme", "https://l/a.png", "https://l/b.png"), payment.brand)
        assertNull(payment.childPlatform)
        val json = wire(payment)
        assertFalse(json.containsKey("child_platform"))
        val brand = json["brand"]!!.jsonObject
        assertEquals("Acme", brand["name"]!!.toString().trim('"'))
        assertEquals("https://l/a.png", brand["logo_url"]!!.toString().trim('"'))
        assertEquals("https://l/b.png", brand["logo_light_url"]!!.toString().trim('"'))
    }

    @Test
    fun `setBrand omits null logo keys`() {
        val payment = PaymentBuilder().setBrand("Acme").build()
        val brand = obj(payment, "brand")
        assertEquals(setOf("name"), brand.keys)
    }

    @Test
    fun `setChildPlatform delegates to setBrand and produces the identical wire body`() {
        val viaBrand = PaymentBuilder().addDestination(address).setBrand("Acme", "https://l/a.png").build()
        val viaOld = PaymentBuilder().addDestination(address).setChildPlatform("Acme", "https://l/a.png").build()
        assertEquals(viaBrand.brand, viaOld.brand)
        assertNull(viaOld.childPlatform)
        assertFalse(wire(viaOld).containsKey("child_platform"))
        assertEquals(wire(viaBrand).filterKeys { it == "brand" }, wire(viaOld).filterKeys { it == "brand" })
    }

    @Test
    fun `a payment with only the deprecated property serializes as brand`() {
        val payment = Payment(childPlatform = Platform("Old", "https://l/o.png"))
        val json = wire(payment)
        assertFalse(json.containsKey("child_platform"))
        assertEquals("Old", json["brand"]!!.jsonObject["name"]!!.toString().trim('"'))
        assertEquals(setOf("name", "logo_url"), json["brand"]!!.jsonObject.keys)
    }

    @Test
    fun `brand wins over the deprecated property`() {
        val payment = Payment(brand = Platform("New"), childPlatform = Platform("Old"))
        val json = wire(payment)
        assertFalse(json.containsKey("child_platform"))
        assertEquals("New", json["brand"]!!.jsonObject["name"]!!.toString().trim('"'))
    }

    @Test
    fun `no brand means neither key is sent`() {
        val json = wire(Payment())
        assertFalse(json.containsKey("brand"))
        assertFalse(json.containsKey("child_platform"))
    }

    @Test
    fun `parsing accepts brand and mirrors it into the deprecated property`() {
        val payment = paymentFromJson("""{"destinations":[],"brand":{"name":"B","logo_url":"u","logo_light_url":"l"}}""")
        assertEquals(Platform("B", "u", "l"), payment.brand)
        assertEquals(payment.brand, payment.childPlatform)
    }

    @Test
    fun `parsing accepts child_platform and populates brand`() {
        val payment = paymentFromJson("""{"destinations":[],"child_platform":{"name":"C","logo_url":"u"}}""")
        assertEquals(Platform("C", "u"), payment.brand)
        assertEquals(Platform("C", "u"), payment.childPlatform)
    }

    @Test
    fun `parsing prefers brand when both are present`() {
        val payment = paymentFromJson(
            """{"destinations":[],"brand":{"name":"B"},"child_platform":{"name":"C"}}"""
        )
        assertEquals("B", payment.brand?.name)
        assertEquals("B", payment.childPlatform?.name)
    }

    @Test
    fun `parsing falls back to child_platform when brand is null`() {
        val payment = paymentFromJson("""{"destinations":[],"brand":null,"child_platform":{"name":"C"}}""")
        assertEquals("C", payment.brand?.name)
    }

    @Test
    fun `parsing with neither leaves both absent`() {
        val payment = paymentFromJson("""{"destinations":[],"brand":null,"child_platform":null}""")
        assertNull(payment.brand)
        assertNull(payment.childPlatform)
    }

    @Test
    fun `addPayment leaves the caller unchanged and the copy carries brand`() = runTest {
        val client = FakeClient()
        val service = BrantaService(BrantaApiOptions(environment = BrantaEnvironment.Localhost), client, FixedSecret("dek", "s"))
        val payment = PaymentBuilder()
            .addDestination(address, DestinationType.BitcoinAddress)
            .setBrand("Acme", "http://localhost/a.png")
            .build()
        val before = payment.copy()
        service.addPayment(payment)
        val posted = client.posted!!
        assertEquals(Platform("Acme", "http://localhost/a.png"), posted.brand)
        assertNotSame(payment.brand, posted.brand)
        assertEquals(before, payment)
        assertEquals(address, payment.destinations.single().value)
        val json = wire(posted)
        assertFalse(json.containsKey("child_platform"))
        assertEquals("Acme", json["brand"]!!.jsonObject["name"]!!.toString().trim('"'))
    }

    @Test
    fun `addPayment normalizes an old-style childPlatform into brand without touching the caller`() = runTest {
        val client = FakeClient()
        val service = BrantaService(BrantaApiOptions(environment = BrantaEnvironment.Localhost), client, FixedSecret("dek", "s"))
        val payment = Payment(childPlatform = Platform("Old"))
        payment.destinations.add(pro.branta.models.Destination(value = address, type = DestinationType.BitcoinAddress, id = "id-1"))
        service.addPayment(payment)
        assertNull(payment.brand)
        assertEquals("Old", payment.childPlatform?.name)
        val posted = client.posted!!
        assertEquals("Old", posted.brand?.name)
        assertNotSame(payment.childPlatform, posted.brand)
        assertFalse(wire(posted).containsKey("child_platform"))
        assertEquals("Old", obj(posted, "brand")["name"]!!.toString().trim('"'))
    }

    @Test
    fun `addPayment with both set sends brand`() = runTest {
        val client = FakeClient()
        val service = BrantaService(BrantaApiOptions(environment = BrantaEnvironment.Localhost), client, FixedSecret("dek", "s"))
        val payment = Payment(brand = Platform("New"), childPlatform = Platform("Old"))
        payment.destinations.add(pro.branta.models.Destination(value = address, type = DestinationType.BitcoinAddress, id = "id-1"))
        service.addPayment(payment)
        assertEquals("New", client.posted!!.brand?.name)
        assertEquals("New", obj(client.posted!!, "brand")["name"]!!.toString().trim('"'))
    }
}
