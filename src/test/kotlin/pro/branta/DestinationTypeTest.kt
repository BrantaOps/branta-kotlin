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
        assertEquals("usdt_base", DestinationType.UsdtBase.wire)
        assertEquals("usdc_base", DestinationType.UsdcBase.wire)
        assertEquals("usdt_arbitrum", DestinationType.UsdtArbitrum.wire)
        assertEquals("usdc_arbitrum", DestinationType.UsdcArbitrum.wire)
        assertEquals(18, DestinationType.entries.size)
        assertNull(DestinationType.fromWire("usdc_ton"))
    }

    @Test
    fun `evm token rails cover five networks with one type each`() {
        val rails = DestinationType.EvmTokenRails
        assertEquals(setOf(1L, 137L, 8453L, 42161L, 56L), rails.map { it.chainId }.toSet())
        assertEquals(10, rails.size)
        assertEquals(10, rails.map { it.type }.toSet().size)
        rails.forEach { assertEquals(it.contract.lowercase(), it.contract) }
    }

    @Test
    fun `evmTokenType selects by chain id and contract`() {
        val baseUsdc = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"
        assertEquals(DestinationType.UsdcBase, DestinationType.evmTokenType(8453, baseUsdc))
        assertEquals(DestinationType.UsdcBase, DestinationType.evmTokenType(8453, baseUsdc.lowercase()))
        // Same contract on the wrong chain is not a rail.
        assertNull(DestinationType.evmTokenType(1, baseUsdc))
        // Polygon bridged USDC.e is not the native USDC contract.
        assertNull(DestinationType.evmTokenType(137, "0x2791bca1f2de4661ed88a30c99a7a9449aa84174"))
        // Arbitrum Nova is not Arbitrum One.
        assertNull(DestinationType.evmTokenType(42170, "0xaf88d065e77c8cc2239327c5edb3a432268e5831"))
    }

    @Test
    fun `secret key helper covers bitcoin, evm rails, and post-only types`() {
        assertTrue(DestinationType.isSecretKeyAddress(DestinationType.BitcoinAddress))
        DestinationType.EvmTokenRails.forEach {
            assertTrue(DestinationType.isSecretKeyAddress(it.type), it.type.wire)
        }
        // Tron, TON, and Solana still encrypt on addPayment. They have no verify URI.
        listOf(
            DestinationType.UsdtTron, DestinationType.UsdtTon,
            DestinationType.UsdtSolana, DestinationType.UsdcSolana
        ).forEach { assertTrue(DestinationType.isSecretKeyAddress(it), it.wire) }
        assertFalse(DestinationType.isSecretKeyAddress(DestinationType.ArkAddress))
        assertFalse(DestinationType.isSecretKeyAddress(DestinationType.SilentPayment))
        assertFalse(DestinationType.isSecretKeyAddress(DestinationType.Bolt11))
        assertEquals(DestinationType.UsdtTron, DestinationType.fromWire("usdt_tron"))
        assertEquals(DestinationType.UsdcArbitrum, DestinationType.fromWire("usdc_arbitrum"))
    }
}
