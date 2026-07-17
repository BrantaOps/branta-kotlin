package pro.branta.v2.interfaces

import pro.branta.BrantaClientOptions
import pro.branta.v2.models.AddPaymentResult
import pro.branta.v2.models.Payment
import pro.branta.v2.models.PaymentsResult

interface IBrantaService {
    suspend fun getPaymentsByQrCode(qrText: String, options: BrantaClientOptions? = null): PaymentsResult
    suspend fun getPayments(destinationValue: String, destinationEncryptionKey: String? = null, options: BrantaClientOptions? = null): PaymentsResult
    suspend fun addPayment(payment: Payment, options: BrantaClientOptions? = null): AddPaymentResult
    suspend fun isApiKeyValid(options: BrantaClientOptions? = null): Boolean
}
