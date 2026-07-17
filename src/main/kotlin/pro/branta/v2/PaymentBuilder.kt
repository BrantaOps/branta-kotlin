package pro.branta.v2

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pro.branta.enums.DestinationType
import pro.branta.v2.models.Destination
import pro.branta.v2.models.Payment
import pro.branta.v2.models.Platform
import java.util.UUID

class PaymentBuilder {
    private val destinations = mutableListOf<Destination>()
    private var description: String? = null
    private var metadata: String? = null
    private var ttl: Int = 0
    private var platformLogoUrl: String? = null
    private var childPlatform: Platform? = null

    fun addDestination(address: String, type: DestinationType? = null): PaymentBuilder {
        destinations.add(Destination(value = address, type = type, isZk = false))
        return this
    }

    fun setZk(): PaymentBuilder {
        val idx = destinations.lastIndex
        if (idx >= 0) {
            destinations[idx] = destinations[idx].copy(
                isZk = true,
                zkId = UUID.randomUUID().toString()
            )
        }
        return this
    }

    fun setDescription(description: String): PaymentBuilder {
        this.description = description
        return this
    }

    fun addMetadata(key: String, value: String): PaymentBuilder {
        val map: MutableMap<String, String> = if (!metadata.isNullOrEmpty()) {
            val parsed = Json.parseToJsonElement(metadata!!).jsonObject
            parsed.entries.associate { it.key to it.value.toString().trim('"') }.toMutableMap()
        } else {
            mutableMapOf()
        }
        map[key] = value
        metadata = Json.encodeToString(JsonObject(map.mapValues { JsonPrimitive(it.value) }))
        return this
    }

    fun setTtl(ttl: Int): PaymentBuilder {
        this.ttl = ttl
        return this
    }

    fun setPlatformLogoUrl(platformLogoUrl: String): PaymentBuilder {
        this.platformLogoUrl = platformLogoUrl
        return this
    }

    fun setChildPlatform(name: String, logoUrl: String? = null, logoLightUrl: String? = null): PaymentBuilder {
        childPlatform = Platform(name = name, logoUrl = logoUrl, logoLightUrl = logoLightUrl)
        return this
    }

    fun build(): Payment = Payment(
        description = description,
        destinations = destinations,
        ttl = ttl,
        metadata = metadata,
        platformLogoUrl = platformLogoUrl,
        childPlatform = childPlatform
    )
}
