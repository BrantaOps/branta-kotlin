package pro.branta

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.branta.enums.BrantaEnvironment
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.exceptions.IncompleteException
import pro.branta.models.Destination
import pro.branta.models.Payment
import java.io.IOException
import kotlin.test.assertFailsWith

class BrantaClientTest {
    private val otherOrigin = "https://attacker.example"
    private val destinations = """[{"value":"test-destination","zk":true}]"""

    private fun clientWithResponse(body: String, statusCode: Int = 200, apiKey: String? = null): BrantaClient {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("status")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return HttpBrantaClient(
            BrantaApiOptions(environment = BrantaEnvironment.Localhost, apiKey = apiKey),
            httpClient
        )
    }

    @Test
    fun `checks every payment logo url`() = runTest {
        val json = """[
            {"destinations": $destinations},
            {"destinations": $destinations, "platform_logo_url": "$otherOrigin/logo.png"}
        ]"""
        val ex = assertFailsWith<BrantaPaymentException> { clientWithResponse(json).getPayments("value") }
        assertTrue(ex.message!!.contains("platformLogoUrl"))
        assertTrue(ex.message!!.contains("configured API domain"))
    }

    @Test
    fun `checks parent and child logo fields`() = runTest {
        val json = """[{"destinations": $destinations, "parent_platform": {"logo_light_url": "$otherOrigin/light.png"}}]"""
        val ex = assertFailsWith<BrantaPaymentException> { clientWithResponse(json).getPayments("value") }
        assertTrue(ex.message!!.contains("parentPlatform.logoLightUrl"))
    }

    @Test
    fun `404 returns an empty list and 429 is incomplete`() = runTest {
        assertTrue(clientWithResponse("", 404).getPayments("value").isEmpty())
        assertFailsWith<IncompleteException> { clientWithResponse("", 429).getPayments("value") }
    }

    @Test
    fun `network failure is incomplete`() = runTest {
        val httpClient = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()
        val client = HttpBrantaClient(BrantaApiOptions(environment = BrantaEnvironment.Localhost), httpClient)
        assertFailsWith<IncompleteException> { client.getPayments("value") }
    }

    @Test
    fun `post 401 and a missing api key are unauthorized`() = runTest {
        val payment = Payment(destinations = mutableListOf(Destination(value = "bc1q")))
        val unauthorized = assertFailsWith<BrantaPaymentException> {
            clientWithResponse("", 401, apiKey = "key").postPayment(payment)
        }
        assertEquals("Unauthorized", unauthorized.message)
        val missing = assertFailsWith<BrantaPaymentException> {
            clientWithResponse("""{"destinations":[]}""").postPayment(payment)
        }
        assertEquals("Unauthorized", missing.message)
    }

    @Test
    fun `post omits hmac headers when no secret is configured`() = runTest {
        var sawHmac = false
        val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            sawHmac = chain.request().header("X-HMAC-Signature") != null
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"destinations":[{"value":"cipher","zk":true}]}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val client = HttpBrantaClient(
            BrantaApiOptions(environment = BrantaEnvironment.Localhost, apiKey = "key"),
            httpClient
        )
        client.postPayment(Payment(destinations = mutableListOf(Destination(value = "cipher"))))
        assertTrue(!sawHmac)
    }
}
