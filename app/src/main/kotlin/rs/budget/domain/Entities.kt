package rs.budget.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

enum class AccountKind { SPENDING, SAVINGS }
enum class TransactionType { EXPENSE, INCOME, TRANSFER }
enum class TransactionSource { MANUAL, RECEIPT, RECURRING }

@Serializable
data class Member(val id: String, val displayName: String, val deviceId: String)

@Serializable
data class Account(
    val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    val archived: Boolean = false,
)

@Serializable
data class Category(val id: String, val name: String, val color: String, val archived: Boolean = false)

@Serializable
data class Transaction(
    val id: String,
    val type: TransactionType,
    val money: Money,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val note: String,
    val merchant: String,
    val paidBy: String,
    val source: TransactionSource,
) {
    init {
        require(!money.isNegative && !money.isZero) { "Transaction amount must be positive" }
        if (type == TransactionType.TRANSFER) {
            require(toAccountId != null && toAccountId != accountId) { "Transfer needs a different destination account" }
        } else {
            require(toAccountId == null) { "Only transfers have a destination account" }
        }
    }
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
