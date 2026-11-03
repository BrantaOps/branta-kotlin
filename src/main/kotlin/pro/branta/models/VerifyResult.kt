package pro.branta.models

import pro.branta.enums.VerifyStatus

data class VerifyResult(
    val status: VerifyStatus,
    val verifyUrl: String,
    val payments: List<Payment> = emptyList()
)
