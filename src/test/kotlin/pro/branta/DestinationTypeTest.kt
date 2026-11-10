package pro.branta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    }

    @Test
    fun `polygon evm helpers`() {
        assertEquals(
            listOf(DestinationType.UsdtPolygon, DestinationType.UsdcPolygon),
            DestinationType.PolygonEvm
        )
        assertTrue(DestinationType.isPolygonEvm(DestinationType.UsdtPolygon))
        assertTrue(DestinationType.isPolygonEvm(DestinationType.UsdcPolygon))
        assertFalse(DestinationType.isPolygonEvm(DestinationType.BitcoinAddress))
        assertTrue(DestinationType.isSecretKeyAddress(DestinationType.BitcoinAddress))
        assertTrue(DestinationType.isSecretKeyAddress(DestinationType.UsdtPolygon))
        assertFalse(DestinationType.isSecretKeyAddress(DestinationType.Bolt11))
    }
}
