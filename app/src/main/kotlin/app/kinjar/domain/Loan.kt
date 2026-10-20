package app.kinjar.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Loan(
    val id: String,
    val name: String,
    val opening: Money,
    @Serializable(with = LocalDateSerializer::class) val start: LocalDate,
    val usualPayment: Money? = null,
    @Serializable(with = LocalDateSerializer::class) val nextDue: LocalDate? = null,
    val archived: Boolean = false,
) {
    init {
        require(name.isNotBlank() && !opening.isNegative)
        require(usualPayment == null || (usualPayment.currency == opening.currency && !usualPayment.isNegative && !usualPayment.isZero))
    }
}

@Serializable
data class LoanPayment(
    val id: String,
    val loanId: String,
    val transactionId: String,
    val total: Money,
    val principal: Money?,
) {
    init {
        require(!total.isNegative && !total.isZero)
        require(principal == null || (principal.currency == total.currency && !principal.isNegative && principal.amount <= total.amount))
    }

    val interest: Money? get() = principal?.let { total - it }
}

@Serializable
data class LoanAdjustment(
    val id: String,
    val loanId: String,
    val amount: Money,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val reason: String,
) {
    init { require(!amount.isZero && reason.isNotBlank()) }
}

data class LoanBalance(val beforeUnknownPayments: Money, val incompletePayments: Int, val invalidHistory: Boolean) {
    val complete: Boolean get() = incompletePayments == 0 && !invalidHistory
    val paidOff: Boolean get() = complete && beforeUnknownPayments.isZero
}
