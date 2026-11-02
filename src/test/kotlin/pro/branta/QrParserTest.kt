package pro.branta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.branta.enums.DestinationType

class QrParserTest {
    @Test
    fun `control character inside the scheme still types the address`() {
        val parser = QrParser("bitcoi\tn:bc1qtest")
        assertEquals(DestinationType.BitcoinAddress, parser.destinationType)
        assertEquals("bc1qtest", parser.destination)
    }

    @Test
    fun `lnurl and lno are not destination types`() {
        assertNull(QrParser("lnurl1example").destinationType)
        assertNull(QrParser("lno1qqexample").destinationType)
        val offer = QrParser("bitcoin:bc1qmerchant?lno=lno1example")
        assertTrue(offer.hasUnverifiableInstruction)
        assertTrue(offer.destinations.none { it.value.startsWith("lno") })
    }

    @Test
    fun `plus in a query value stays a plus`() {
        val parser = QrParser("bitcoin:bc1qtest?branta_id=ab&branta_secret=a+b")
        assertEquals("a+b", parser.onChainEncryptionSecret)
    }

    @Test
    fun `tb1 and bcrt1 are bitcoin addresses`() {
        assertEquals(DestinationType.BitcoinAddress, QrParser("tb1qtest").destinationType)
        assertEquals(DestinationType.BitcoinAddress, QrParser("bcrt1qtest").destinationType)
    }

    @Test
    fun `encodeUriComponent matches encodeURIComponent`() {
        assertEquals("a%2Bb%2Fc%3D%20d!*'()~", encodeUriComponent("a+b/c= d!*'()~"))
    }
}
