package pro.branta.v2

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import pro.branta.enums.DestinationType

class QrParserTest {

    @Test
    fun `plain bitcoin address detected`() {
        val parser = QrParser("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        assertEquals("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", parser.destination)
        assertEquals(DestinationType.BitcoinAddress, parser.destinationType)
    }

    @Test
    fun `plain bech32 bitcoin address detected`() {
        val parser = QrParser("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        assertEquals(DestinationType.BitcoinAddress, parser.destinationType)
    }

    @Test
    fun `bitcoin uri extracts address`() {
        val parser = QrParser("bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        assertEquals("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", parser.destination)
        assertEquals(DestinationType.BitcoinAddress, parser.destinationType)
    }

    @Test
    fun `bitcoin uri extracts branta_id and branta_secret`() {
        val parser = QrParser("bitcoin:bc1qaddr?branta_id=encryptedId&branta_secret=mySecret")
        assertEquals("encryptedId", parser.onChainEncryptionText)
        assertEquals("mySecret", parser.onChainEncryptionSecret)
        assertTrue(parser.isOnChainZk())
    }

    @Test
    fun `bitcoin uri with url-encoded branta_id decodes correctly`() {
        val encoded = "bitcoin:addr?branta_id=abc%2Bdef%3D%3D&branta_secret=secret"
        val parser = QrParser(encoded)
        assertEquals("abc+def==", parser.onChainEncryptionText)
        assertEquals("secret", parser.onChainEncryptionSecret)
    }

    @Test
    fun `bitcoin uri with lightning param adds bolt11 destination`() {
        val parser = QrParser("bitcoin:addr?lightning=lnbc100n1ptest")
        assertEquals(2, parser.destinations.size)
        assertEquals("lnbc100n1ptest", parser.destinations[1].value)
        assertEquals(DestinationType.Bolt11, parser.destinations[1].type)
    }

    @Test
    fun `bitcoin uri with ark param adds ark destination`() {
        val parser = QrParser("bitcoin:addr?ark=ark1testaddress")
        assertEquals(2, parser.destinations.size)
        assertEquals("ark1testaddress", parser.destinations[1].value)
        assertEquals(DestinationType.ArkAddress, parser.destinations[1].type)
    }

    @Test
    fun `bitcoin uri with silent_payment param adds silent payment destination`() {
        val parser = QrParser("bitcoin:addr?silent_payment=sp1testaddress")
        assertEquals(2, parser.destinations.size)
        assertEquals(DestinationType.SilentPayment, parser.destinations[1].type)
    }

    @Test
    fun `lightning uri with bolt11 invoice`() {
        val parser = QrParser("lightning:lnbc100n1ptest")
        assertEquals("lnbc100n1ptest", parser.destination)
        assertEquals(DestinationType.Bolt11, parser.destinationType)
    }

    @Test
    fun `lightning uri is case insensitive`() {
        val parser = QrParser("LIGHTNING:lnbc100n1ptest")
        assertEquals(DestinationType.Bolt11, parser.destinationType)
    }

    @Test
    fun `plain bolt11 invoice detected`() {
        val parser = QrParser("lnbc100n1ptest")
        assertEquals(DestinationType.Bolt11, parser.destinationType)
    }

    @Test
    fun `plain lnurl detected`() {
        val parser = QrParser("LNURL1TESTVALUE")
        assertEquals(DestinationType.LnUrl, parser.destinationType)
    }

    @Test
    fun `plain ark address detected`() {
        val parser = QrParser("ark1testaddress")
        assertEquals(DestinationType.ArkAddress, parser.destinationType)
    }

    @Test
    fun `plain signet ark address detected`() {
        val parser = QrParser("tark1testaddress")
        assertEquals(DestinationType.ArkAddress, parser.destinationType)
    }

    @Test
    fun `plain silent payment detected`() {
        val parser = QrParser("sp1testaddress")
        assertEquals(DestinationType.SilentPayment, parser.destinationType)
    }

    @Test
    fun `plain tsp1 silent payment detected`() {
        val parser = QrParser("tsp1testaddress")
        assertEquals(DestinationType.SilentPayment, parser.destinationType)
    }

    @Test
    fun `ethereum tether address detected`() {
        val parser = QrParser("0x" + "a".repeat(40))
        assertEquals(DestinationType.TetherAddress, parser.destinationType)
    }

    @Test
    fun `tron tether address detected`() {
        val parser = QrParser("T" + "a".repeat(33))
        assertEquals(DestinationType.TetherAddress, parser.destinationType)
    }

    @Test
    fun `ln address email format detected`() {
        val parser = QrParser("user@example.com")
        assertEquals(DestinationType.LnAddress, parser.destinationType)
    }

    @Test
    fun `isOnChainZk returns false when no branta params`() {
        val parser = QrParser("bitcoin:addr")
        assertFalse(parser.isOnChainZk())
    }

    @Test
    fun `isOnChainZk returns false when only branta_id present`() {
        val parser = QrParser("bitcoin:addr?branta_id=enc")
        assertFalse(parser.isOnChainZk())
    }

    @Test
    fun `bip321 bc and tb params are on-chain addresses`() {
        val parser = QrParser("bitcoin:?bc=bc1qmerchant&tb=tb1qtest")

        assertEquals(listOf("bc1qmerchant", "tb1qtest"), parser.destinations.map { it.value })
        assertEquals(DestinationType.BitcoinAddress, parser.destinations[0].type)
        assertEquals(DestinationType.BitcoinAddress, parser.destinations[1].type)
        assertFalse(parser.hasUnverifiableInstruction)
    }

    @Test
    fun `sp param adds silent payment`() {
        val parser = QrParser("bitcoin:bc1qmerchant?sp=sp1qqexample")

        assertEquals("sp1qqexample", parser.destinations[1].value)
        assertEquals(DestinationType.SilentPayment, parser.destinations[1].type)
        assertFalse(parser.hasUnverifiableInstruction)
    }

    @Test
    fun `lno and pay params are unverifiable`() {
        val lno = QrParser("bitcoin:bc1qmerchant?lno=lno1qqexample")
        assertEquals("lno1qqexample", lno.destinations[1].value)
        assertEquals(DestinationType.Bolt12, lno.destinations[1].type)
        assertTrue(lno.hasUnverifiableInstruction)

        val pay = QrParser("bitcoin:bc1qmerchant?pay=bitcoin:bc1qother")
        assertTrue(pay.hasUnverifiableInstruction)
        assertEquals(listOf("bc1qmerchant"), pay.destinations.map { it.value })
    }

    @Test
    fun `bolt12 and lnurl instructions are unverifiable`() {
        assertTrue(QrParser("bitcoin:bc1qmerchant?bolt12=lno1qqexample").hasUnverifiableInstruction)
        assertTrue(QrParser("bitcoin:bc1qmerchant?lightning=LNURL1example").hasUnverifiableInstruction)
        assertTrue(QrParser("lightning:LNURL1example").hasUnverifiableInstruction)
        assertTrue(QrParser("lightning:lno1qqexample").hasUnverifiableInstruction)
    }

    @Test
    fun `hash-zk instructions are bindable`() {
        val parser = QrParser("bitcoin:bc1qmerchant?lightning=lnbc100n1ptest&ark=ark1example&silent_payment=sp1qqexample")
        assertFalse(parser.hasUnverifiableInstruction)
        assertFalse(QrParser("lightning:lnbc100n1ptest").hasUnverifiableInstruction)
    }

    @Test
    fun `whitespace is trimmed from input`() {
        val parser = QrParser("  1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa  ")
        assertEquals("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", parser.destination)
    }
}
