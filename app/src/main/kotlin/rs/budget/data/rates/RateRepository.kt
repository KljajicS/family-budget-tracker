package rs.budget.data.rates

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import rs.budget.data.Clock
import rs.budget.data.EventStore
import rs.budget.data.db.BudgetDb
import rs.budget.domain.Currency
import rs.budget.domain.ExchangeQuote
import rs.budget.domain.ExchangeRateOverrideSet
import rs.budget.domain.ExchangeRateSnapshot
import rs.budget.domain.validRate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RateRepository(private val db: BudgetDb, private val store: EventStore, private val clock: Clock) {
    val snapshots = combine(
        db.exchangeRateQueries.all().asFlow().mapToList(Dispatchers.IO),
        db.exchangeRateOverrideQueries.all().asFlow().mapToList(Dispatchers.IO),
    ) { cached, overrides ->
        ExchangeRateSnapshot(
            cached.associate { Currency.valueOf(it.currency) to ExchangeQuote(it.rate.toBigDecimal(), LocalDate.parse(it.effective_date), Instant.ofEpochMilli(it.fetched_at)) },
            overrides.filter { it.rate != null }.associate { Currency.valueOf(it.currency) to it.rate!!.toBigDecimal() },
        )
    }.flowOn(Dispatchers.Default)

    suspend fun setOverride(currency: Currency, rate: String?) {
        store.append(ExchangeRateOverrideSet(currency, rate))
    }

    suspend fun cache(rates: Map<Currency, BigDecimal>, effectiveDate: LocalDate) = withContext(Dispatchers.IO) {
        require(rates.keys == setOf(Currency.EUR, Currency.USD) && rates.values.all(::validRate))
        val now = clock.now()
        require(!effectiveDate.isAfter(now.atZone(BELGRADE).toLocalDate()))
        db.transaction {
            val previous = db.exchangeRateQueries.all().executeAsList()
            require(previous.none { LocalDate.parse(it.effective_date).isAfter(effectiveDate) })
            rates.forEach { (currency, rate) ->
                db.exchangeRateQueries.upsert(currency.name, rate.toPlainString(), effectiveDate.toString(), now.toEpochMilli())
            }
        }
    }

    companion object {
        val BELGRADE: ZoneId = ZoneId.of("Europe/Belgrade")
    }
}
