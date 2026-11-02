package pro.branta.models

import pro.branta.enums.DestinationType

data class Destination(
    var value: String,
    var isPrimary: Boolean = false,
    /** Runtime-only. Set from the wire `zk` flag and cleared after a successful decrypt. */
    var isEncrypted: Boolean = true,
    var type: DestinationType? = null,
    /** Wire field is `zk_id`. */
    var id: String? = null,
    var encryptedDek: String? = null
)
