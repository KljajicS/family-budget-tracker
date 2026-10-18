package rs.budget.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.LocalDate

class ActivityFiltersTest {
    private val day = LocalDate.of(2026, 10, 18)
    private val expense = Transaction("tx", TransactionType.EXPENSE, Money.of("10", Currency.RSD),
        "cash", null, "food", day, "", "", "member", TransactionSource.MANUAL)

    @Test fun `dates include both endpoints and combine with category and type`() {
        assertTrue(matchesActivity(expense, day, day, "food", "EXPENSE"))
        assertFalse(matchesActivity(expense, day.plusDays(1), null, null, null))
        assertFalse(matchesActivity(expense, null, day.minusDays(1), null, null))
        assertFalse(matchesActivity(expense, day, day, "other", "EXPENSE"))
        assertFalse(matchesActivity(expense, day, day, "food", "INCOME"))
    }

    @Test fun `unfiltered history includes every transaction type and uncategorized entries`() {
        TransactionType.entries.forEach { type ->
            val tx = expense.copy(type = type, categoryId = null, toAccountId = if (type == TransactionType.TRANSFER) "bank" else null)
            assertTrue(matchesActivity(tx, null, null, null, null))
            assertTrue(matchesActivity(tx, null, null, null, type.name))
            assertFalse(matchesActivity(tx, null, null, "food", null))
        }
    }
}
