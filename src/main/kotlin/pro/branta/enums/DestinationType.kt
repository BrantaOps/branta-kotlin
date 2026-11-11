package pro.branta.enums

enum class DestinationType(val wire: String) {
    BitcoinAddress("bitcoin_address"),
    Bolt11("bolt11"),
    ArkAddress("ark_address"),
    SilentPayment("silent_payment"),
    UsdtPolygon("usdt_polygon"),
    UsdcPolygon("usdc_polygon"),
    UsdtEthereum("usdt_ethereum"),
    UsdcEthereum("usdc_ethereum"),
    UsdtBsc("usdt_bsc"),
    UsdcBsc("usdc_bsc"),
    UsdtTron("usdt_tron"),
    UsdtTon("usdt_ton"),
    UsdcTon("usdc_ton"),
    UsdtSolana("usdt_solana"),
    UsdcSolana("usdc_solana");

    companion object {
        /** Polygon EVM secret-key rails. A `pol:` URI maps to this family for verify (match-if-any). */
        val PolygonEvm: List<DestinationType> = listOf(UsdtPolygon, UsdcPolygon)

        val EthereumEvm: List<DestinationType> = listOf(UsdtEthereum, UsdcEthereum)

        val BscEvm: List<DestinationType> = listOf(UsdtBsc, UsdcBsc)

        val Tron: List<DestinationType> = listOf(UsdtTron)

        val Ton: List<DestinationType> = listOf(UsdtTon, UsdcTon)

        val Solana: List<DestinationType> = listOf(UsdtSolana, UsdcSolana)

        /** URI scheme -> secret-key destination family for verify (match-if-any). */
        val SecretKeyUriFamilies: Map<String, List<DestinationType>> = mapOf(
            "pol" to PolygonEvm,
            "eth" to EthereumEvm,
            "bsc" to BscEvm,
            "tron" to Tron,
            "ton" to Ton,
            "sol" to Solana
        )

        fun fromWire(value: String?): DestinationType? = entries.firstOrNull { it.wire == value }

        fun isPolygonEvm(type: DestinationType): Boolean = type in PolygonEvm

        /** Address rails encrypted with a random branta_secret (not a derived key). */
        fun isSecretKeyAddress(type: DestinationType): Boolean =
            type == BitcoinAddress || SecretKeyUriFamilies.values.any { type in it }
    }
}
