package pro.branta.v2.interfaces

import pro.branta.BrantaClientOptions
import pro.branta.v2.models.Payment

interface IBrantaClient {
    suspend fun getPayments(destinationValue: String, options: BrantaClientOptions? = null): List<Payment>
    suspend fun postPayment(payment: Payment, options: BrantaClientOptions? = null): Payment?
    suspend fun isApiKeyValid(options: BrantaClientOptions? = null): Boolean
}
