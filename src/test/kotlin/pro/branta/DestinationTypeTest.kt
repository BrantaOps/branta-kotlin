package pro.branta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.branta.enums.DestinationType

class DestinationTypeTest {
    @Test
    fun `wire format is exact`() {
        assertEquals("bitcoin_address", DestinationType.BitcoinAddress.wire)
        assertEquals("bolt11", DestinationType.Bolt11.wire)
        assertEquals("ark_address", DestinationType.ArkAddress.wire)
        assertEquals("silent_payment", DestinationType.SilentPayment.wire)
        assertEquals("usdt_polygon", DestinationType.UsdtPolygon.wire)
        assertEquals("usdc_polygon", DestinationType.UsdcPolygon.wire)
        assertEquals("usdt_ethereum", DestinationType.UsdtEthereum.wire)
        assertEquals("usdc_ethereum", DestinationType.UsdcEthereum.wire)
        assertEquals("usdt_bsc", DestinationType.UsdtBsc.wire)
        assertEquals("usdc_bsc", DestinationType.UsdcBsc.wire)
        assertEquals("usdt_tron", DestinationType.UsdtTron.wire)
        assertEquals("usdt_ton", DestinationType.UsdtTon.wire)
        assertEquals("usdt_solana", DestinationType.UsdtSolana.wire)
        assertEquals("usdc_solana", DestinationType.UsdcSolana.wire)
        assertEquals(14, DestinationType.entries.size)
        assertNull(DestinationType.fromWire("usdc_ton"))
    }

    @Test
    fun `uri scheme families and secret key helper`() {
        assertEquals(setOf("pol", "eth", "bsc", "tron", "ton", "sol"), DestinationType.SecretKeyUriFamilies.keys)
        assertEquals(listOf(DestinationType.UsdtEthereum, DestinationType.UsdcEthereum), DestinationType.EthereumEvm)
        assertEquals(listOf(DestinationType.UsdtBsc, DestinationType.UsdcBsc), DestinationType.BscEvm)
        assertEquals(listOf(DestinationType.UsdtTron), DestinationType.Tron)
        assertEquals(listOf(DestinationType.UsdtTon), DestinationType.Ton)
        assertEquals(listOf(DestinationType.UsdtSolana, DestinationType.UsdcSolana), DestinationType.Solana)
        assertEquals(listOf(DestinationType.UsdtPolygon, DestinationType.UsdcPolygon), DestinationType.PolygonEvm)
        assertTrue(DestinationType.isSecretKeyAddress(DestinationType.BitcoinAddress))
        DestinationType.SecretKeyUriFamilies.values.flatten().forEach {
            assertTrue(DestinationType.isSecretKeyAddress(it), it.wire)
        }
        assertFalse(DestinationType.isSecretKeyAddress(DestinationType.ArkAddress))
        assertFalse(DestinationType.isSecretKeyAddress(DestinationType.SilentPayment))
        assertFalse(DestinationType.isSecretKeyAddress(DestinationType.Bolt11))
        assertEquals(DestinationType.UsdtTron, DestinationType.fromWire("usdt_tron"))
    }
}
