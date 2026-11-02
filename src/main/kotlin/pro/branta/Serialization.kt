package pro.branta

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pro.branta.enums.DestinationType
import pro.branta.models.Destination
import pro.branta.models.Payment
import pro.branta.models.Platform

private val json = Json { explicitNulls = false }

internal fun paymentToJson(payment: Payment): String =
    json.encodeToString(JsonElement.serializer(), paymentToElement(payment))

internal fun paymentFromJson(text: String): Payment =
    paymentFromElement(json.parseToJsonElement(text))

internal fun paymentsFromJson(text: String): List<Payment> {
    val parsed = json.parseToJsonElement(text)
    if (parsed !is JsonArray) return emptyList()
    return parsed.map { paymentFromElement(it) }
}

private fun paymentToElement(payment: Payment): JsonObject = buildJsonObject {
    put("destinations", buildJsonArray { payment.destinations.forEach { add(destinationToElement(it)) } })
    payment.createdAt?.let { put("created_at", it) }
    payment.ttl?.let { put("ttl", it) }
    payment.metadata?.let { put("metadata", it) }
    payment.platform?.let { put("platform", it) }
    payment.platformLogoUrl?.let { put("platform_logo_url", it) }
    payment.platformLogoLightUrl?.let { put("platform_logo_light_url", it) }
    payment.childPlatform?.let { put("child_platform", platformToElement(it)) }
}

private fun destinationToElement(destination: Destination): JsonObject = buildJsonObject {
    put("value", destination.value)
    put("primary", destination.isPrimary)
    put("zk", true)
    destination.type?.let { put("type", it.wire) }
    destination.id?.let { put("zk_id", it) }
    destination.encryptedDek?.let { put("encrypted_dek", it) }
}

private fun platformToElement(platform: Platform): JsonObject = buildJsonObject {
    platform.name?.let { put("name", it) }
    platform.logoUrl?.let { put("logo_url", it) }
    platform.logoLightUrl?.let { put("logo_light_url", it) }
}

private fun paymentFromElement(element: JsonElement): Payment {
    val obj = element.jsonObject
    val destinations = obj["destinations"]?.takeUnless { it is JsonNull }?.jsonArray
        ?.map { destinationFromElement(it) }?.toMutableList()
        ?: mutableListOf()
    return Payment(
        destinations = destinations,
        createdAt = obj.string("created_at"),
        ttl = obj["ttl"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
        metadata = obj.string("metadata"),
        platform = obj.string("platform"),
        platformLogoUrl = obj.string("platform_logo_url"),
        platformLogoLightUrl = obj.string("platform_logo_light_url"),
        parentPlatform = obj["parent_platform"]?.takeUnless { it is JsonNull }?.let { platformFromElement(it) },
        childPlatform = obj["child_platform"]?.takeUnless { it is JsonNull }?.let { platformFromElement(it) }
    )
}

private fun destinationFromElement(element: JsonElement): Destination {
    val obj = element.jsonObject
    return Destination(
        value = obj.string("value") ?: "",
        isPrimary = obj["primary"]?.jsonPrimitive?.booleanOrNull ?: false,
        // Guardrail `zk: false` is a leftover unencrypted row — do not try to decrypt it.
        isEncrypted = obj["zk"]?.jsonPrimitive?.booleanOrNull == true,
        type = DestinationType.fromWire(obj.string("type")),
        id = obj.string("zk_id"),
        encryptedDek = obj.string("encrypted_dek")
    )
}

private fun platformFromElement(element: JsonElement): Platform {
    val obj = element.jsonObject
    return Platform(
        name = obj.string("name"),
        logoUrl = obj.string("logo_url"),
        logoLightUrl = obj.string("logo_light_url")
    )
}

private fun JsonObject.string(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return element.jsonPrimitive.contentOrNull
}

internal fun parseMetadataMap(metadata: String?): MutableMap<String, JsonElement> {
    if (metadata.isNullOrEmpty()) return mutableMapOf()
    return try {
        val parsed = json.parseToJsonElement(metadata)
        if (parsed is JsonObject) parsed.toMutableMap() else mutableMapOf()
    } catch (_: Exception) {
        mutableMapOf()
    }
}

internal fun foldDescriptionIntoMetadata(payment: Payment) {
    val description = payment.description
    if (description.isNullOrEmpty()) return
    val map = parseMetadataMap(payment.metadata)
    if (!map.containsKey("description")) map["description"] = JsonPrimitive(description)
    payment.metadata = json.encodeToString(JsonElement.serializer(), JsonObject(map))
    payment.description = null
}

internal fun liftDescriptionFromMetadata(payment: Payment) {
    val description = parseMetadataMap(payment.metadata)["description"]
    if (description is JsonPrimitive && description.isString) payment.description = description.content
}
