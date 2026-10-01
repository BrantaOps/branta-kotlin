package pro.branta.v2

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pro.branta.v2.models.Payment

class PaymentSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `btc pay plugin version uses the api field name`() {
        val encoded = json.encodeToString(Payment(btcPayServerPluginVersion = "1.2.3"))
        val obj = Json.parseToJsonElement(encoded).jsonObject

        assertEquals("1.2.3", obj["btc_pay_server_plugin_version"]?.jsonPrimitive?.content)
        assertFalse(obj.containsKey("btcpay_server_plugin_version"))

        val decoded = json.decodeFromString<Payment>("""{"btc_pay_server_plugin_version":"1.2.3"}""")
        assertEquals("1.2.3", decoded.btcPayServerPluginVersion)
    }
}
