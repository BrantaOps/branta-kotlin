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
    /** Deprecated old name for [brand]. Still read when [brand] is not set. */
    @property:Deprecated("Use brand", ReplaceWith("brand"))
    var childPlatform: Platform? = null,
    /** Runtime-only flag set after metadata decryption. */
    var isMetadataDecrypted: Boolean = false,
    /** The payee a shared-key platform tags the payment with: a name plus optional logo source URLs. */
    var brand: Platform? = null
)

/** `brand` if set, otherwise the deprecated `childPlatform`. The one place that reads the old name. */
@Suppress("DEPRECATION")
internal fun Payment.effectiveBrand(): Platform? = brand ?: childPlatform
