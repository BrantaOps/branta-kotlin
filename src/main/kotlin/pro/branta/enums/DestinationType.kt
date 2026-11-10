package pro.branta.enums

enum class DestinationType(val wire: String) {
    BitcoinAddress("bitcoin_address"),
    Bolt11("bolt11"),
    ArkAddress("ark_address"),
    SilentPayment("silent_payment"),
    UsdtPolygon("usdt_polygon"),
    UsdcPolygon("usdc_polygon");

    companion object {
        /** Polygon EVM secret-key rails. A `pol:` URI maps to this family for verify (match-if-any). */
        val PolygonEvm: List<DestinationType> = listOf(UsdtPolygon, UsdcPolygon)

        fun fromWire(value: String?): DestinationType? = entries.firstOrNull { it.wire == value }

        fun isPolygonEvm(type: DestinationType): Boolean =
            type == UsdtPolygon || type == UsdcPolygon

        /** Address rails encrypted with a random branta_secret (not a derived key). */
        fun isSecretKeyAddress(type: DestinationType): Boolean =
            type == BitcoinAddress || isPolygonEvm(type)
    }
}
