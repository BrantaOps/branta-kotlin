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
    UsdtSolana("usdt_solana"),
    UsdcSolana("usdc_solana"),
    UsdtBase("usdt_base"),
    UsdcBase("usdc_base"),
    UsdtArbitrum("usdt_arbitrum"),
    UsdcArbitrum("usdc_arbitrum");

    /** An ERC-681 `ethereum:<contract>@<chainId>/transfer` rail. */
    internal data class EvmTokenRail(
        val chainId: Long,
        /** Lowercase, so lookup is case-insensitive. */
        val contract: String,
        val type: DestinationType
    )

    companion object {
        /** Chain id plus token contract select one destination type. */
        internal val EvmTokenRails: List<EvmTokenRail> = listOf(
            EvmTokenRail(1, "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", UsdcEthereum),
            EvmTokenRail(1, "0xdac17f958d2ee523a2206206994597c13d831ec7", UsdtEthereum),
            EvmTokenRail(137, "0x3c499c542cef5e3811e1192ce70d8cc03d5c3359", UsdcPolygon),
            EvmTokenRail(137, "0xc2132d05d31c914a87c6611c10748aeb04b58e8f", UsdtPolygon),
            EvmTokenRail(8453, "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913", UsdcBase),
            EvmTokenRail(8453, "0xfde4c96c8593536e31f229ea8f37b2ada2699bb2", UsdtBase),
            EvmTokenRail(42161, "0xaf88d065e77c8cc2239327c5edb3a432268e5831", UsdcArbitrum),
            EvmTokenRail(42161, "0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9", UsdtArbitrum),
            EvmTokenRail(56, "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d", UsdcBsc),
            EvmTokenRail(56, "0x55d398326f99059ff775485246999027b3197955", UsdtBsc)
        )

        internal fun evmTokenType(chainId: Long, contract: String): DestinationType? {
            val needle = contract.lowercase()
            return EvmTokenRails.firstOrNull { it.chainId == chainId && it.contract == needle }?.type
        }

        /**
         * Address rails `addPayment` encrypts with one UUID secret.
         * Tron, TON, and Solana are included because `addPayment` still encrypts them.
         * They have no verify URI.
         */
        private val UuidSecretTypes: Set<DestinationType> =
            setOf(BitcoinAddress, UsdtTron, UsdtTon, UsdtSolana, UsdcSolana) + EvmTokenRails.map { it.type }

        fun fromWire(value: String?): DestinationType? = entries.firstOrNull { it.wire == value }

        /** Address rails encrypted with a random branta_secret (not a derived key). */
        internal fun isSecretKeyAddress(type: DestinationType): Boolean = type in UuidSecretTypes
    }
}
