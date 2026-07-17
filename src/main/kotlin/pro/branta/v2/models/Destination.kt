package pro.branta.v2.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import pro.branta.enums.DestinationType

@Serializable
data class Destination(
    var value: String,
    @SerialName("primary") val isPrimary: Boolean = false,
    @SerialName("zk") var isZk: Boolean = false,
    val type: DestinationType? = null,
    val zkId: String? = null,
    @SerialName("encrypted_dek") var encryptedDek: String? = null,
    @Transient var isEncrypted: Boolean = false
)
