package pro.branta.models

data class AddPaymentResult(
    val payment: Payment,
    val secret: String,
    val verifyUrl: String
) {
    override fun toString(): String =
        "AddPaymentResult(payment=$payment, secret=${redacted(secret)}, verifyUrl=${verifyUrl.substringBefore('#')})"
}

private fun redacted(value: String?): String = if (value.isNullOrEmpty()) "null" else "***"
