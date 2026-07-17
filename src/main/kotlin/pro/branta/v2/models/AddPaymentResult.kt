package pro.branta.v2.models

data class AddPaymentResult(
    val payment: Payment,
    val secret: String,
    val verifyUrl: String
)
