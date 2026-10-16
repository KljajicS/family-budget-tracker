package rs.budget.data

import android.content.Context
import androidx.core.content.edit
import rs.budget.domain.TransactionType

class TransactionAccountStore(
    private val read: (TransactionType) -> String?,
    private val write: (TransactionType, String) -> Unit,
) {
    fun lastUsed(type: TransactionType): String? = if (type == TransactionType.TRANSFER) null else read(type)

    fun remember(type: TransactionType, accountId: String) {
        if (type != TransactionType.TRANSFER) write(type, accountId)
    }

    companion object {
        fun fromPreferences(context: Context): TransactionAccountStore {
            val prefs = context.getSharedPreferences("transaction_accounts", Context.MODE_PRIVATE)
            return TransactionAccountStore(
                { prefs.getString(it.name, null) },
                { type, accountId -> prefs.edit { putString(type.name, accountId) } },
            )
        }
    }
}
