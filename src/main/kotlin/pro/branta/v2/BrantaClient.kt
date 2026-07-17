package pro.branta.v2

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import pro.branta.BrantaClientOptions
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.v2.interfaces.IBrantaClient
import pro.branta.v2.models.Payment
import java.io.IOException
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BrantaClient(
    private val defaultOptions: BrantaClientOptions? = null,
    private val httpClient: OkHttpClient = OkHttpClient()
) : IBrantaClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    override suspend fun getPayments(destinationValue: String, options: BrantaClientOptions?): List<Payment> {
        val baseUrl = defaultOptions.getBaseUrl(options)
        val encoded = URLEncoder.encode(destinationValue, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/v2/payments/$encoded")
            .get()
            .build()

        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) return emptyList()

        val body = response.body?.string() ?: return emptyList()
        if (body.isBlank() || body == "null") return emptyList()

        val payments = json.decodeFromString<List<Payment>>(body)
        verifyLogoUrls(baseUrl, payments)
        return payments
    }

    override suspend fun postPayment(payment: Payment, options: BrantaClientOptions?): Payment? {
        val baseUrl = defaultOptions.getBaseUrl(options)
        val apiKey = defaultOptions.getApiKey(options)
            ?: throw BrantaPaymentException("Unauthorized")

        val body = json.encodeToString(payment)
        val requestBody = body.toRequestBody("application/json".toMediaType())

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val requestBuilder = Request.Builder()
            .url("$baseUrl/v2/payments")
            .post(requestBody)
            .header("Authorization", "Bearer $apiKey")

        val hmacSecret = defaultOptions.getHmacSecret(options)
        if (hmacSecret != null) {
            val message = "POST|$baseUrl/v2/payments|$body|$timestamp"
            val signature = hmacSha256(hmacSecret, message)
            requestBuilder
                .header("X-HMAC-Signature", signature)
                .header("X-HMAC-Timestamp", timestamp)
        }

        val response = httpClient.newCall(requestBuilder.build()).await()
        if (!response.isSuccessful) throw BrantaPaymentException(response.code.toString())

        val responseBody = response.body?.string() ?: return null
        if (responseBody.isBlank() || responseBody == "null") return null
        return json.decodeFromString<Payment>(responseBody)
    }

    override suspend fun isApiKeyValid(options: BrantaClientOptions?): Boolean {
        val baseUrl = defaultOptions.getBaseUrl(options)
        val apiKey = defaultOptions.getApiKey(options)
            ?: throw BrantaPaymentException("Unauthorized")

        val request = Request.Builder()
            .url("$baseUrl/v2/api-keys/health-check")
            .get()
            .header("Authorization", "Bearer $apiKey")
            .build()

        val response = httpClient.newCall(request).await()
        return response.isSuccessful
    }

    private fun verifyLogoUrls(baseUrl: String, payments: List<Payment>) {
        val baseOrigin = java.net.URI(baseUrl).let { "${it.scheme}://${it.host}${if (it.port != -1) ":${it.port}" else ""}" }
        for (payment in payments) {
            val logoUrl = payment.platformLogoUrl ?: continue
            if (logoUrl.isEmpty()) continue
            val logoUri = java.net.URI(logoUrl)
            val logoOrigin = "${logoUri.scheme}://${logoUri.host}${if (logoUri.port != -1) ":${logoUri.port}" else ""}"
            if (logoOrigin != baseOrigin) {
                throw BrantaPaymentException("platformLogoUrl domain does not match the configured baseUrl domain")
            }
        }
    }

    private fun hmacSha256(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hashBytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
    })
}
