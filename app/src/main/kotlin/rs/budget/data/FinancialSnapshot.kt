package rs.budget.data

import rs.budget.domain.*
import java.time.LocalDate

data class FinancialSnapshot(
    val day: LocalDate,
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val budgets: List<BudgetStatus>,
    val bills: List<Bill>,
    val goals: List<GoalStatus>,
    val home: HomeSummary,
    val exchange: ExchangeRateSnapshot,
    val missingCurrencies: Set<Currency>,
)
