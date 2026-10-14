package rs.budget.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
sealed interface Event

@Serializable @SerialName("MemberCreated")
data class MemberCreated(val member: Member) : Event

@Serializable @SerialName("AccountCreated")
data class AccountCreated(val account: Account) : Event

@Serializable @SerialName("AccountArchived")
data class AccountArchived(val accountId: String) : Event

@Serializable @SerialName("CategoryCreated")
data class CategoryCreated(val category: Category) : Event

@Serializable @SerialName("TransactionAdded")
data class TransactionAdded(val transaction: Transaction) : Event

@Serializable @SerialName("TransactionEdited")
data class TransactionEdited(val transaction: Transaction) : Event

@Serializable @SerialName("TransactionDeleted")
data class TransactionDeleted(val transactionId: String) : Event

@Serializable @SerialName("BudgetSet")
data class BudgetSet(val budget: Budget) : Event

/** One row of the append-only log. `seq` is per device and strictly increasing. */
data class EventRecord(
    val id: String,
    val deviceId: String,
    val seq: Long,
    val occurredAt: Instant,
    val event: Event,
)

val EventJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}
