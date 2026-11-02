package pro.branta.models

data class Payment(
    var description: String? = null,
    val destinations: MutableList<Destination> = mutableListOf(),
    var createdAt: String? = null,
    /** Time to live in seconds. Null is omitted; the server default is 7 days. Posted 0 is 30 seconds. */
    var ttl: Int? = null,
    var metadata: String? = null,
    var platform: String? = null,
    var platformLogoUrl: String? = null,
    var platformLogoLightUrl: String? = null,
    var parentPlatform: Platform? = null,
    var childPlatform: Platform? = null,
    /** Runtime-only flag set after metadata decryption. */
    var isMetadataDecrypted: Boolean = false
)
