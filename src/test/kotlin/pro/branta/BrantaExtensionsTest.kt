package pro.branta

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import pro.branta.enums.DestinationType
import pro.branta.v2.*

class BrantaExtensionsTest {

    @Test
    fun `isBolt11 returns true for lnbc prefix`() = assertTrue("lnbc100n1ptest".isBolt11())

    @Test
    fun `isBolt11 returns true for lntb prefix`() = assertTrue("lntb100n1ptest".isBolt11())

    @Test
    fun `isBolt11 returns true for lnbcrt prefix`() = assertTrue("lnbcrt100n1ptest".isBolt11())

    @Test
    fun `isBolt11 returns true case insensitive`() = assertTrue("LNBC100N1PTEST".isBolt11())

    @Test
    fun `isBolt11 returns false for non-bolt11`() = assertFalse("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa".isBolt11())

    @Test
    fun `isArk returns true for ark1 prefix`() = assertTrue("ark1testaddress".isArk())

    @Test
    fun `isArk returns true case insensitive`() = assertTrue("ARK1TESTADDRESS".isArk())

    @Test
    fun `isArk returns false for non-ark`() = assertFalse("bc1qtest".isArk())

    @Test
    fun `isSilentPayment returns true for sp1 prefix`() = assertTrue("sp1testaddress".isSilentPayment())

    @Test
    fun `isSilentPayment returns true for tsp1 prefix`() = assertTrue("tsp1testaddress".isSilentPayment())

    @Test
    fun `isSilentPayment returns false for non-silent`() = assertFalse("bc1qtest".isSilentPayment())

    @Test
    fun `getHashZkType returns Bolt11 for bolt11 invoice`() =
        assertEquals(DestinationType.Bolt11, "lnbc100n1ptest".getHashZkType())

    @Test
    fun `getHashZkType returns ArkAddress for ark address`() =
        assertEquals(DestinationType.ArkAddress, "ark1testaddress".getHashZkType())

    @Test
    fun `getHashZkType returns SilentPayment for silent payment`() =
        assertEquals(DestinationType.SilentPayment, "sp1testaddress".getHashZkType())

    @Test
    fun `getHashZkType returns null for bitcoin address`() =
        assertNull("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa".getHashZkType())

    @Test
    fun `toNormalizedHash lowercases before hashing`() {
        assertEquals("lnbc100n1ptest".toNormalizedHash(), "LNBC100N1PTEST".toNormalizedHash())
    }

    @Test
    fun `toNormalizedHash returns uppercase hex`() {
        val hash = "test".toNormalizedHash()
        assertTrue(hash.matches(Regex("[0-9A-F]+")))
    }

    @Test
    fun `toUrlFragment produces correct fragment string`() {
        val keys = linkedMapOf("id1" to "secret1", "id2" to "secret2")
        assertEquals("#k-id1=secret1&k-id2=secret2", keys.toUrlFragment())
    }

    @Test
    fun `toUrlFragment with single key`() {
        assertEquals("#k-abc=xyz", mapOf("abc" to "xyz").toUrlFragment())
    }
}
