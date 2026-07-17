package pro.branta.v2

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import pro.branta.enums.DestinationType

class PaymentBuilderTest {

    @Test
    fun `addDestination adds a destination with type`() {
        val payment = PaymentBuilder()
            .addDestination("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", DestinationType.BitcoinAddress)
            .build()
        assertEquals(1, payment.destinations.size)
        assertEquals("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", payment.destinations[0].value)
        assertEquals(DestinationType.BitcoinAddress, payment.destinations[0].type)
        assertFalse(payment.destinations[0].isZk)
    }

    @Test
    fun `setZk marks last destination as ZK and assigns a zkId`() {
        val payment = PaymentBuilder()
            .addDestination("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", DestinationType.BitcoinAddress)
            .setZk()
            .build()
        assertTrue(payment.destinations[0].isZk)
        assertNotNull(payment.destinations[0].zkId)
        assertTrue(payment.destinations[0].zkId!!.isNotEmpty())
    }

    @Test
    fun `setZk on multiple destinations applies to last only`() {
        val payment = PaymentBuilder()
            .addDestination("addr1", DestinationType.BitcoinAddress)
            .setZk()
            .addDestination("lnbc100n1ptest", DestinationType.Bolt11)
            .setZk()
            .build()
        assertTrue(payment.destinations[0].isZk)
        assertTrue(payment.destinations[1].isZk)
        assertNotEquals(payment.destinations[0].zkId, payment.destinations[1].zkId)
    }

    @Test
    fun `setDescription sets description on payment`() {
        val payment = PaymentBuilder()
            .addDestination("addr", DestinationType.BitcoinAddress)
            .setDescription("Test description")
            .build()
        assertEquals("Test description", payment.description)
    }

    @Test
    fun `addMetadata builds json metadata`() {
        val payment = PaymentBuilder()
            .addDestination("addr", DestinationType.BitcoinAddress)
            .addMetadata("email", "test@example.com")
            .addMetadata("name", "Alice")
            .build()
        assertNotNull(payment.metadata)
        assertTrue(payment.metadata!!.contains("email"))
        assertTrue(payment.metadata!!.contains("test@example.com"))
        assertTrue(payment.metadata!!.contains("name"))
    }

    @Test
    fun `setTtl sets ttl on payment`() {
        val payment = PaymentBuilder()
            .addDestination("addr", DestinationType.BitcoinAddress)
            .setTtl(600)
            .build()
        assertEquals(600, payment.ttl)
    }

    @Test
    fun `setPlatformLogoUrl sets logo url on payment`() {
        val payment = PaymentBuilder()
            .addDestination("addr", DestinationType.BitcoinAddress)
            .setPlatformLogoUrl("https://example.com/logo.png")
            .build()
        assertEquals("https://example.com/logo.png", payment.platformLogoUrl)
    }

    @Test
    fun `setChildPlatform sets child platform on payment`() {
        val payment = PaymentBuilder()
            .addDestination("addr", DestinationType.BitcoinAddress)
            .setChildPlatform("ChildBrand", "https://child.com/logo.png")
            .build()
        assertNotNull(payment.childPlatform)
        assertEquals("ChildBrand", payment.childPlatform!!.name)
        assertEquals("https://child.com/logo.png", payment.childPlatform!!.logoUrl)
    }

    @Test
    fun `builder is chainable`() {
        val payment = PaymentBuilder()
            .addDestination("addr1", DestinationType.BitcoinAddress)
            .setZk()
            .addDestination("lnbc100n1ptest", DestinationType.Bolt11)
            .setZk()
            .setDescription("My invoice")
            .setTtl(300)
            .build()
        assertEquals(2, payment.destinations.size)
        assertEquals("My invoice", payment.description)
        assertEquals(300, payment.ttl)
        assertTrue(payment.destinations.all { it.isZk })
    }

    @Test
    fun `setZk with no destinations does nothing`() {
        val payment = PaymentBuilder().setZk().build()
        assertTrue(payment.destinations.isEmpty())
    }
}
