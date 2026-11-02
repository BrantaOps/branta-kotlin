package pro.branta

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.exceptions.IncompleteException
import pro.branta.models.Payment
import java.io.IOException
import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface BrantaClient {
    suspend fun getPayments(destinationValue: String, options: BrantaApiOptions? = null): List<Payment>
    suspend fun postPayment(payment: Payment, options: BrantaApiOptions? = null): Payment?
    suspend fun isApiKeyValid(options: BrantaApiOptions? = null): Boolean
}

internal class HttpBrantaClient(
    private val defaultOptions: BrantaApiOptions? = null,
    private val httpClient: OkHttpClient = OkHttpClient()
) : BrantaClient {

    override suspend fun getPayments(destinationValue: String, options: BrantaApiOptions?): List<Payment> {
        val baseUrl = defaultOptions.environmentUrl(options)
        val request = Request.Builder()
            .url("$baseUrl/v2/payments/${encodeUriComponent(destinationValue)}")
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).await()
        } catch (e: IOException) {
            throw IncompleteException(e.message ?: e.toString())
        }

        response.use {
            if (it.code == 404) return emptyList()
            if (!it.isSuccessful) throw IncompleteException(statusText(it.code))
            val body = it.body?.string().orEmpty()
            if (body.isEmpty()) return emptyList()
            val payments = paymentsFromJson(body)
            verifyLogoUrls(baseUrl, payments)
            return payments
        }
    }

    override suspend fun postPayment(payment: Payment, options: BrantaApiOptions?): Payment? {
        val baseUrl = defaultOptions.environmentUrl(options)
        val body = paymentToJson(payment)
        val requestBuilder = Request.Builder()
            .url("$baseUrl/v2/payments")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${requireApiKey(options)}")
        applyHmacHeaders(requestBuilder, baseUrl, body, options)

        val response = httpClient.newCall(requestBuilder.build()).await()
        response.use {
            if (!it.isSuccessful) throw BrantaPaymentException(statusText(it.code))
            val responseBody = it.body?.string().orEmpty()
            if (responseBody.isEmpty()) return null
            return paymentFromJson(responseBody)
        }
    }

    override suspend fun isApiKeyValid(options: BrantaApiOptions?): Boolean {
        val baseUrl = defaultOptions.environmentUrl(options)
        val request = Request.Builder()
            .url("$baseUrl/v2/api-keys/health-check")
            .get()
            .header("Authorization", "Bearer ${requireApiKey(options)}")
            .build()
        val response = httpClient.newCall(request).await()
        response.use { return it.isSuccessful }
    }

    private fun requireApiKey(options: BrantaApiOptions?): String =
        defaultOptions.apiKey(options) ?: throw BrantaPaymentException("Unauthorized")

    private fun applyHmacHeaders(
        requestBuilder: Request.Builder,
        baseUrl: String,
        body: String,
        options: BrantaApiOptions?
    ) {
        val hmacSecret = defaultOptions.hmacSecret(options) ?: return
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val trimmedBase = baseUrl.trimEnd('/')
        val message = "POST|$trimmedBase/v2/payments|$body|$timestamp"
        requestBuilder
            .header("X-HMAC-Signature", hmacSha256(hmacSecret, message))
            .header("X-HMAC-Timestamp", timestamp)
    }

    private fun verifyLogoUrls(baseUrl: String, payments: List<Payment>) {
        val baseOrigin = originOf(baseUrl) ?: return

        fun check(logoUrl: String?, fieldName: String) {
            if (logoUrl.isNullOrEmpty()) return
            val logoOrigin = originOf(logoUrl)
            if (logoOrigin != baseOrigin) {
                throw BrantaPaymentException("$fieldName domain does not match the configured API domain")
            }
        }

        for (payment in payments) {
            check(payment.platformLogoUrl, "platformLogoUrl")
            check(payment.platformLogoLightUrl, "platformLogoLightUrl")
            check(payment.parentPlatform?.logoUrl, "parentPlatform.logoUrl")
            check(payment.parentPlatform?.logoLightUrl, "parentPlatform.logoLightUrl")
            check(payment.childPlatform?.logoUrl, "childPlatform.logoUrl")
            check(payment.childPlatform?.logoLightUrl, "childPlatform.logoLightUrl")
        }
    }

    private fun originOf(url: String): String? = try {
        val uri = URI(url)
        if (uri.scheme.isNullOrEmpty() || uri.host.isNullOrEmpty()) null
        else uri.scheme + "://" + uri.host + if (uri.port != -1) ":${uri.port}" else ""
    } catch (_: Exception) {
        null
    }

    private fun hmacSha256(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun statusText(status: Int): String = when (status) {
        400 -> "BadRequest"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "NotFound"
        409 -> "Conflict"
        422 -> "UnprocessableEntity"
        429 -> "TooManyRequests"
        500 -> "InternalServerError"
        502 -> "BadGateway"
        503 -> "ServiceUnavailable"
        504 -> "GatewayTimeout"
        else -> status.toString()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
    })
}
