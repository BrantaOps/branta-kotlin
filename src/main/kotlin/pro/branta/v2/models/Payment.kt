package pro.branta.v2.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import pro.branta.exceptions.BrantaPaymentException

@Serializable
data class Payment(
    val description: String? = null,
    val destinations: MutableList<Destination> = mutableListOf(),
    @SerialName("created_at") val createdAt: String? = null,
    val ttl: Int = 0,
    var metadata: String? = null,
    val platform: String? = null,
    @SerialName("platform_logo_url") val platformLogoUrl: String? = null,
    @SerialName("platform_logo_light_url") val platformLogoLightUrl: String? = null,
    @SerialName("parent_platform") val parentPlatform: Platform? = null,
    @SerialName("child_platform") var childPlatform: Platform? = null,
    @SerialName("btcpay_server_plugin_version") val btcPayServerPluginVersion: String? = null,
    @Transient var isMetadataDecrypted: Boolean = false
) {
    fun getDefaultValue(): String =
        destinations.firstOrNull()?.value ?: throw BrantaPaymentException("No destinations found")
}
