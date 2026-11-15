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

    private val baseUsdcContract = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"
    private val deposit = "0x1111111111111111111111111111111111111111"

    @Test
    fun `erc681 transfer binds the deposit and one token`() {
        val parser = QrParser(
            "ethereum:$baseUsdcContract@8453/transfer?address=$deposit&uint256=1000000&branta_id=abc%2Bdef%3D&branta_secret=1234"
        )
        assertEquals(deposit, parser.destination)
        assertEquals(DestinationType.UsdcBase, parser.destinationType)
        assertEquals(1, parser.destinations.size)
        assertTrue(parser.destinations.single().secretKey)
        assertEquals(listOf(DestinationType.UsdcBase), parser.expectedSecretKeyTypes)
        assertEquals("abc+def=", parser.onChainEncryptionText)
        assertEquals("1234", parser.onChainEncryptionSecret)
    }

    @Test
    fun `every rail binds the deposit and one type`() {
        for (rail in DestinationType.EvmTokenRails) {
            val parser = QrParser(
                "ethereum:${rail.contract}@${rail.chainId}/transfer?address=$deposit&branta_id=a&branta_secret=b"
            )
            assertEquals(deposit, parser.destination, rail.type.wire)
            assertEquals(rail.type, parser.destinationType, rail.type.wire)
            assertEquals(listOf(rail.type), parser.expectedSecretKeyTypes, rail.type.wire)
        }
    }

    @Test
    fun `erc681 contract matches case-insensitively`() {
        val parser = QrParser("ethereum:${baseUsdcContract.lowercase()}@8453/transfer?address=$deposit")
        assertEquals(DestinationType.UsdcBase, parser.destinationType)
    }

    @Test
    fun `erc681 uint256 is not part of the binding`() {
        for (amount in listOf("1", "50000000", "999999999999999999999")) {
            val parser = QrParser("ethereum:$baseUsdcContract@8453/transfer?address=$deposit&uint256=$amount")
            assertEquals(deposit, parser.destination, amount)
            assertEquals(DestinationType.UsdcBase, parser.destinationType, amount)
        }
    }

    @Test
    fun `erc681 tolerates one leading slash and an upper-case scheme`() {
        assertEquals(
            DestinationType.UsdcBase,
            QrParser("ethereum:/$baseUsdcContract@8453/transfer?address=$deposit").destinationType
        )
        assertEquals(
            DestinationType.UsdcBase,
            QrParser("ETHEREUM:$baseUsdcContract@8453/transfer?address=$deposit").destinationType
        )
    }

    @Test
    fun `erc681 accepts an upper-case 0X deposit prefix`() {
        val parser = QrParser("ethereum:$baseUsdcContract@8453/transfer?address=0X${deposit.substring(2)}")
        assertEquals(DestinationType.UsdcBase, parser.destinationType)
    }

    @Test
    fun `erc681 first address param wins`() {
        val parser = QrParser(
            "ethereum:$baseUsdcContract@8453/transfer?address=$deposit&address=0x${"f".repeat(40)}"
        )
        assertEquals(deposit, parser.destination)
    }

    @Test
    fun `percent-encoded ampersand stays inside branta_id`() {
        val parser = QrParser(
            "ethereum:$baseUsdcContract@8453/transfer?address=$deposit&branta_id=a%26b&branta_secret=s"
        )
        assertEquals("a&b", parser.onChainEncryptionText)
        assertEquals("s", parser.onChainEncryptionSecret)
    }

    @Test
    fun `erc681 params in the fragment are not read`() {
        val parser = QrParser(
            "ethereum:$baseUsdcContract@8453/transfer?address=$deposit#&branta_id=abc&branta_secret=1234"
        )
        assertEquals(DestinationType.UsdcBase, parser.destinationType)
        assertNull(parser.onChainEncryptionText)
    }

    @Test
    fun `a query after a fragment is not read`() {
        val parser = QrParser(
            "ethereum:$baseUsdcContract@8453/transfer#x?address=$deposit&branta_id=a&branta_secret=b"
        )
        assertNull(parser.destinationType)
        assertNull(parser.onChainEncryptionText)
        assertTrue(parser.expectedSecretKeyTypes.isEmpty())
    }

    @Test
    fun `a fragment after the query is dropped`() {
        val parser = QrParser(
            "ethereum:$baseUsdcContract@8453/transfer?address=$deposit&branta_id=a&branta_secret=b#address=0x2"
        )
        assertEquals(deposit, parser.destination)
        assertEquals("a", parser.onChainEncryptionText)
        assertEquals("b", parser.onChainEncryptionSecret)
    }

    @Test
    fun `bitcoin query after a fragment is not read`() {
        val parser = QrParser("bitcoin:bc1qtest#x?branta_id=a&branta_secret=b")
        assertNull(parser.onChainEncryptionText)
        val ok = QrParser("bitcoin:bc1qtest?branta_id=a&branta_secret=b#frag")
        assertEquals("b", ok.onChainEncryptionSecret)
    }

    @Test
    fun `erc681 chain id beyond 15 digits is untyped`() {
        val parser = QrParser(
            "ethereum:$baseUsdcContract@${"9".repeat(5000)}/transfer?address=$deposit"
        )
        assertNull(parser.destinationType)
    }

    @Test
    fun `erc681 leading-zero chain id still selects the rail`() {
        val parser = QrParser(
            "ethereum:$baseUsdcContract@${"0".repeat(4500)}8453/transfer?address=$deposit"
        )
        assertEquals(DestinationType.UsdcBase, parser.destinationType)
    }

    @Test
    fun `incomplete erc681 forms do not bind`() {
        val forms = listOf(
            // No chain id.
            "ethereum:$baseUsdcContract/transfer?address=$deposit&branta_id=abc&branta_secret=1234",
            // Payment-request prefix and ENS-style forms.
            "ethereum:pay-$baseUsdcContract@8453/transfer?address=$deposit&branta_id=abc&branta_secret=1234",
            "ethereum:vitalik.eth@8453/transfer?address=$deposit&branta_id=abc&branta_secret=1234",
            // // makes the contract an authority, not a path.
            "ethereum://$baseUsdcContract@8453/transfer?address=$deposit&branta_id=abc&branta_secret=1234",
            // Only transfer is a payment. approve and transferFrom are not.
            "ethereum:$baseUsdcContract@8453/approve?address=$deposit&branta_id=abc&branta_secret=1234",
            "ethereum:$baseUsdcContract@8453/transferFrom?address=$deposit&branta_id=abc&branta_secret=1234",
            // Path parameters after the function name.
            "ethereum:$baseUsdcContract@8453/transfer;x=1?address=$deposit&branta_id=abc&branta_secret=1234",
            // Chain id forms that are not plain digits.
            "ethereum:$baseUsdcContract@0x2105/transfer?address=$deposit&branta_id=abc&branta_secret=1234",
            "ethereum:$baseUsdcContract@-8453/transfer?address=$deposit&branta_id=abc&branta_secret=1234",
            "ethereum:$baseUsdcContract@+8453/transfer?address=$deposit&branta_id=abc&branta_secret=1234",
            "ethereum:$baseUsdcContract@/transfer?address=$deposit&branta_id=abc&branta_secret=1234",
            // No address, a truncated address, or an ENS recipient.
            "ethereum:$baseUsdcContract@8453/transfer?branta_id=abc&branta_secret=1234",
            "ethereum:$baseUsdcContract@8453/transfer?address=0x1234&branta_id=abc&branta_secret=1234",
            "ethereum:$baseUsdcContract@8453/transfer?address=vitalik.eth&branta_id=abc&branta_secret=1234"
        )
        for (uri in forms) {
            val parser = QrParser(uri)
            assertNull(parser.destinationType, uri)
            assertTrue(parser.expectedSecretKeyTypes.isEmpty(), uri)
            assertNull(parser.onChainEncryptionText, uri)
            assertFalse(parser.destinations.first().secretKey, uri)
        }
    }

    @Test
    fun `unknown contract does not bind`() {
        val parser = QrParser(
            "ethereum:0x${"2".repeat(40)}@8453/transfer?address=$deposit&branta_id=a&branta_secret=b"
        )
        assertNull(parser.destinationType)
        assertNull(parser.onChainEncryptionText)
    }

    @Test
    fun `a contract on the wrong chain does not bind`() {
        val parser = QrParser(
            "ethereum:$baseUsdcContract@1/transfer?address=$deposit&branta_id=a&branta_secret=b"
        )
        assertNull(parser.destinationType)
        assertNull(parser.onChainEncryptionText)
    }

    @Test
    fun `unparseable authority is an untyped destination`() {
        val parser = QrParser("ethereum://[bad/transfer")
        assertNull(parser.destinationType)
        assertEquals("ethereum://[bad/transfer", parser.destination)
        // Other schemes never throw either. They carry no Branta params.
        assertNull(QrParser("bitcoin://[bad").onChainEncryptionText)
    }

    @Test
    fun `removed chain schemes do not bind`() {
        for (scheme in listOf("pol", "eth", "bsc", "tron", "ton", "sol")) {
            val text = "$scheme:0x278297528c32eCa01295B0d2135984d3442ec963?value=50&branta_id=abc&branta_secret=1234"
            val parser = QrParser(text)
            assertEquals(text, parser.destination, scheme)
            assertNull(parser.destinationType, scheme)
            assertFalse(parser.destinations.single().secretKey, scheme)
            assertTrue(parser.expectedSecretKeyTypes.isEmpty(), scheme)
            assertFalse(parser.hasBrantaParams(), scheme)
        }
    }

    @Test
    fun `bitcoin uri expects the bitcoin secret-key family`() {
        val parser = QrParser("bitcoin:bc1qmerchant?branta_id=enc&branta_secret=sec")
        assertTrue(parser.destinations.first().secretKey)
        assertEquals(listOf(DestinationType.BitcoinAddress), parser.expectedSecretKeyTypes)
    }
}
