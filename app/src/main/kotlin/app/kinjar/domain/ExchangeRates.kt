package app.kinjar.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class ExchangeQuote(val rate: BigDecimal, val effectiveDate: LocalDate, val fetchedAt: Instant)

data class ExchangeRateSnapshot(
    val cached: Map<Currency, ExchangeQuote> = emptyMap(),
    val overrides: Map<Currency, BigDecimal> = emptyMap(),
) {
    val rates = Rates(cached.mapValues { it.value.rate } + overrides)
}
