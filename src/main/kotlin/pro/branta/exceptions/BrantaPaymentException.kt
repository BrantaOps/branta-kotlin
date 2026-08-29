package pro.branta.exceptions

enum class BrantaPaymentExceptionReason {
    Tampered
}

class BrantaPaymentException(message: String, val reason: BrantaPaymentExceptionReason? = null) : Exception(message)
