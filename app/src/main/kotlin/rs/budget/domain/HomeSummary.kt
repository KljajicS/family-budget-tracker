package rs.budget.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

data class HomeSummary(
    val safeToSpend: Money,
    val safePerDay: Money,
    val partial: Boolean,
    val daysLeft: Int,
    val income: Money,
    val spent: Money,
    val savingsTotal: Money,
    val savingsByCurrency: Map<Currency, Money>,
    val balances: Map<String, Money>,
)

object HomeCalculator {

    fun summarize(
        today: LocalDate,
        accounts: List<Account>,
        transactions: List<Transaction>,
        rates: Rates,
        committed: Money = Money.zero(Currency.RSD),
    ): HomeSummary {
        val byId = accounts.associateBy { it.id }
        val balances = balances(accounts, transactions)
        var partial = false

        fun rsdOrSkip(m: Money): Money = rates.convert(m) ?: Money.zero(Currency.RSD).also { partial = true }

        val liquid = balances
            .filter { (id, _) -> byId[id]?.kind == AccountKind.SPENDING }
            .values.fold(Money.zero(Currency.RSD)) { acc, m -> acc + rsdOrSkip(m) }

        val savingsByCurrency = balances
            .filter { (id, _) -> byId[id]?.kind == AccountKind.SAVINGS }
            .values.groupBy { it.currency }
            .mapValues { (c, list) -> list.fold(Money.zero(c)) { a, b -> a + b } }
        val savingsTotal = savingsByCurrency.values.fold(Money.zero(Currency.RSD)) { acc, m -> acc + rsdOrSkip(m) }

        val month = YearMonth.from(today)
        val thisMonth = transactions.filter { YearMonth.from(it.date) == month }
        val income = thisMonth.filter { it.type == TransactionType.INCOME }.sumRsd(::rsdOrSkip)
        val spent = thisMonth.filter { it.type == TransactionType.EXPENSE }.sumRsd(::rsdOrSkip)

        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        val safe = liquid - committed
        val perDay = Money.of(safe.amount.divide(BigDecimal(daysLeft), 2, RoundingMode.DOWN), Currency.RSD)

        return HomeSummary(safe, perDay, partial, daysLeft, income, spent, savingsTotal, savingsByCurrency, balances)
    }

    // ponytail: transfers carry the source currency to the destination; the Add screen only allows same-currency
    // transfers in milestone 1, cross-currency transfers get an explicit received amount later.
    fun balances(accounts: List<Account>, transactions: List<Transaction>): Map<String, Money> {
        val result = accounts.associate { it.id to Money.zero(it.currency) }.toMutableMap()
        fun add(accountId: String, delta: Money) {
            val current = result[accountId] ?: return
            // Old events may carry another currency; the account's currency is the truth, so those are skipped, not thrown.
            if (delta.currency != current.currency) return
            result[accountId] = current + delta
        }
        for (t in transactions) {
            when (t.type) {
                TransactionType.INCOME -> add(t.accountId, t.money)
                TransactionType.EXPENSE -> add(t.accountId, -t.money)
                TransactionType.TRANSFER -> {
                    add(t.accountId, -t.money)
                    add(t.toAccountId!!, t.money)
                }
            }
        }
        return result
    }

    private fun List<Transaction>.sumRsd(convert: (Money) -> Money): Money =
        fold(Money.zero(Currency.RSD)) { acc, t -> acc + convert(t.money) }
}
