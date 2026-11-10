package pro.branta.models

import pro.branta.enums.VerifyStatus

data class VerifyResult(
    val status: VerifyStatus,
    val verifyUrl: String,
    val payments: List<Payment> = emptyList(),
    /** Decrypted rails that bound the scan on `Match` (token for `pol:` is on `type`). */
    val matchedDestinations: List<Destination> = emptyList()
)
