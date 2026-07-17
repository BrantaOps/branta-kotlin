package pro.branta.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DestinationType {
    @SerialName("bitcoin_address") BitcoinAddress,
    @SerialName("bolt11") Bolt11,
    @SerialName("bolt12") Bolt12,
    @SerialName("ln_url") LnUrl,
    @SerialName("tether_address") TetherAddress,
    @SerialName("ln_address") LnAddress,
    @SerialName("ark_address") ArkAddress,
    @SerialName("silent_payment") SilentPayment
}
