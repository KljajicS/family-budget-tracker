package rs.budget.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate

@Serializable
data class Receipt(
    val fiscalUrl: String,
    val issuer: String,
    val invoiceNumber: String,
    val merchant: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val total: Money,
    val items: List<ReceiptItem>,
) {
    val id: String get() = MessageDigest.getInstance("SHA-256")
        .digest("rs-fiscal:$issuer:$invoiceNumber".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val transactionId: String get() = "receipt:$id"
}

@Serializable
data class ReceiptItem(
    val name: String,
    @Serializable(with = BigDecimalSerializer::class) val quantity: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val unitPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val total: BigDecimal,
)
