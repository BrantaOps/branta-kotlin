package pro.branta.v2

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.branta.BrantaClientOptions
import pro.branta.enums.BrantaServerBaseUrl
import pro.branta.enums.PrivacyMode
import pro.branta.exceptions.BrantaPaymentException
import kotlin.test.assertFailsWith

class BrantaClientTest {

    // Matches BrantaServerBaseUrl.Localhost's mapped URL.
    private val sameOrigin = "http://localhost:3000"
    private val otherOrigin = "https://attacker.example"

    private fun clientWithResponse(json: String): BrantaClient {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val options = BrantaClientOptions(baseUrl = BrantaServerBaseUrl.Localhost, privacy = PrivacyMode.Loose)
        return BrantaClient(defaultOptions = options, httpClient = httpClient)
    }

    private val destinations = """[{"value":"test-destination"}]"""

    @Test
    fun `checks every payment logo url, not just the first`() = runTest {
        val json = """
            [
                {"destinations": $destinations},
                {"destinations": $destinations, "platform_logo_url": "$otherOrigin/logo.png"}
            ]
        """.trimIndent()
        val client = clientWithResponse(json)

        assertFailsWith<BrantaPaymentException> { client.getPayments("value") }
    }

    @Test
    fun `catches mismatched platformLogoLightUrl`() = runTest {
        val json = """[ {"destinations": $destinations, "platform_logo_light_url": "$otherOrigin/logo-light.png"} ]"""
        val client = clientWithResponse(json)

        val ex = assertFailsWith<BrantaPaymentException> { client.getPayments("value") }
        assertTrue(ex.message!!.contains("platformLogoLightUrl"))
    }

    @Test
    fun `catches mismatched parentPlatform logoUrl`() = runTest {
        val json = """[ {"destinations": $destinations, "parent_platform": {"logo_url": "$otherOrigin/logo.png"}} ]"""
        val client = clientWithResponse(json)

        val ex = assertFailsWith<BrantaPaymentException> { client.getPayments("value") }
        assertTrue(ex.message!!.contains("parentPlatform.logoUrl"))
    }

    @Test
    fun `catches mismatched parentPlatform logoLightUrl`() = runTest {
        val json =
            """[ {"destinations": $destinations, "parent_platform": {"logo_light_url": "$otherOrigin/logo-light.png"}} ]"""
        val client = clientWithResponse(json)

        val ex = assertFailsWith<BrantaPaymentException> { client.getPayments("value") }
        assertTrue(ex.message!!.contains("parentPlatform.logoLightUrl"))
    }

    @Test
    fun `catches mismatched childPlatform logoUrl`() = runTest {
        val json = """[ {"destinations": $destinations, "child_platform": {"logo_url": "$otherOrigin/logo.png"}} ]"""
        val client = clientWithResponse(json)

        val ex = assertFailsWith<BrantaPaymentException> { client.getPayments("value") }
        assertTrue(ex.message!!.contains("childPlatform.logoUrl"))
    }

    @Test
    fun `catches mismatched childPlatform logoLightUrl`() = runTest {
        val json =
            """[ {"destinations": $destinations, "child_platform": {"logo_light_url": "$otherOrigin/logo-light.png"}} ]"""
        val client = clientWithResponse(json)

        val ex = assertFailsWith<BrantaPaymentException> { client.getPayments("value") }
        assertTrue(ex.message!!.contains("childPlatform.logoLightUrl"))
    }

    @Test
    fun `does not throw when all logo fields are same-origin or absent`() = runTest {
        val json = """
            [
                {
                    "destinations": $destinations,
                    "platform_logo_url": "$sameOrigin/a.png",
                    "platform_logo_light_url": "$sameOrigin/b.png",
                    "parent_platform": {"logo_url": "$sameOrigin/c.png", "logo_light_url": "$sameOrigin/d.png"},
                    "child_platform": {"logo_url": "$sameOrigin/e.png"}
                },
                {"destinations": $destinations}
            ]
        """.trimIndent()
        val client = clientWithResponse(json)

        val payments = client.getPayments("value")
        assertEquals(2, payments.size)
    }
}
