package pro.branta.v2.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Platform(
    val name: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("logo_light_url") val logoLightUrl: String? = null
)
