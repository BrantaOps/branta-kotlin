package pro.branta.enums

enum class DestinationType(val wire: String) {
    BitcoinAddress("bitcoin_address"),
    Bolt11("bolt11"),
    ArkAddress("ark_address"),
    SilentPayment("silent_payment");

    companion object {
        fun fromWire(value: String?): DestinationType? = entries.firstOrNull { it.wire == value }
    }
}
