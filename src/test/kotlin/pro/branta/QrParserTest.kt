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

    @Test
    fun `chain uri schemes map to their destination family`() {
        val cases = mapOf(
            "eth" to DestinationType.EthereumEvm,
            "bsc" to DestinationType.BscEvm,
            "tron" to DestinationType.Tron,
            "ton" to DestinationType.Ton,
            "sol" to DestinationType.Solana
        )
        for ((scheme, family) in cases) {
            val parser = QrParser("$scheme:someaddress?value=1&branta_id=enc&branta_secret=sec")
            assertEquals("someaddress", parser.destination, scheme)
            assertNull(parser.destinationType, scheme)
            assertTrue(parser.destinations.single().secretKey, scheme)
            assertEquals(family, parser.expectedSecretKeyTypes, scheme)
            assertEquals("enc", parser.onChainEncryptionText, scheme)
            assertEquals("sec", parser.onChainEncryptionSecret, scheme)
        }
    }

    @Test
    fun `pol uri is secret key without a stamped token type`() {
        val parser = QrParser(
            "pol:0x278297528c32eCa01295B0d2135984d3442ec963?value=50&branta_id=enc&branta_secret=sec"
        )
        assertEquals("0x278297528c32eCa01295B0d2135984d3442ec963", parser.destination)
        assertNull(parser.destinationType)
        assertTrue(parser.destinations.single().secretKey)
        assertEquals("enc", parser.onChainEncryptionText)
        assertEquals("sec", parser.onChainEncryptionSecret)
        assertEquals(DestinationType.PolygonEvm, parser.expectedSecretKeyTypes)
    }

    @Test
    fun `bitcoin uri expects the bitcoin secret-key family`() {
        val parser = QrParser("bitcoin:bc1qmerchant?branta_id=enc&branta_secret=sec")
        assertTrue(parser.destinations.first().secretKey)
        assertEquals(listOf(DestinationType.BitcoinAddress), parser.expectedSecretKeyTypes)
    }
}
