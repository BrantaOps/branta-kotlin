package pro.branta

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pro.branta.enums.DestinationType
import pro.branta.models.Destination
import pro.branta.models.Payment
import pro.branta.models.Platform
import java.util.UUID

class PaymentBuilder {
    private val payment = Payment()

    fun addDestination(value: String, type: DestinationType? = null): PaymentBuilder {
        payment.destinations.add(Destination(value = value, type = type, id = UUID.randomUUID().toString()))
        return this
    }

    fun setDescription(description: String): PaymentBuilder = addMetadata("description", description)

    fun addMetadata(key: String, value: String): PaymentBuilder {
        val map = parseMetadataMap(payment.metadata)
        map[key] = JsonPrimitive(value)
        payment.metadata = Json.encodeToString(JsonElement.serializer(), JsonObject(map))
        return this
    }

    /** Time to live in seconds. */
    fun setTtl(ttl: Int): PaymentBuilder {
        payment.ttl = ttl
        return this
    }

    fun setPlatformLogoUrl(platformLogoUrl: String): PaymentBuilder {
        payment.platformLogoUrl = platformLogoUrl
        return this
    }

    fun setChildPlatform(name: String, logoUrl: String? = null, logoLightUrl: String? = null): PaymentBuilder {
        payment.childPlatform = Platform(name = name, logoUrl = logoUrl, logoLightUrl = logoLightUrl)
        return this
    }

    fun build(): Payment = payment
}
