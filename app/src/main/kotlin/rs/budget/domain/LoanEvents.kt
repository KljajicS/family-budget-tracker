package rs.budget.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @SerialName("LoanCreated")
data class LoanCreated(val loan: Loan) : Event

@Serializable @SerialName("LoanUpdated")
data class LoanUpdated(val loan: Loan) : Event

@Serializable @SerialName("LoanArchived")
data class LoanArchived(val loanId: String) : Event

@Serializable @SerialName("LoanPaymentAdded")
data class LoanPaymentAdded(val payment: LoanPayment) : Event

@Serializable @SerialName("LoanPaymentUpdated")
data class LoanPaymentUpdated(val payment: LoanPayment) : Event

@Serializable @SerialName("LoanPaymentRemoved")
data class LoanPaymentRemoved(val paymentId: String) : Event

@Serializable @SerialName("LoanPrincipalAdjusted")
data class LoanPrincipalAdjusted(val adjustment: LoanAdjustment) : Event

@Serializable @SerialName("LoanReceiptLinked")
data class LoanReceiptLinked(val id: String, val loanId: String, val transactionId: String) : Event
