package pro.branta.models

data class AddPaymentResult(
    val payment: Payment,
    val secret: String,
    val verifyUrl: String
)
