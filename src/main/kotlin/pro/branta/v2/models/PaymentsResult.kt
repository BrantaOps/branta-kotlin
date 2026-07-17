package pro.branta.v2.models

data class PaymentsResult(
    val payments: List<Payment> = emptyList(),
    val verifyUrl: String = ""
)
