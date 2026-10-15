package rs.budget.domain

import java.math.BigDecimal

class Rates(private val toRsd: Map<Currency, BigDecimal>) {
    fun convert(m: Money): Money? {
        if (m.currency == Currency.RSD) return m
        val rate = toRsd[m.currency] ?: return null
        return Money.of(m.amount * rate, Currency.RSD)
    }

    fun convert(m: Money, target: Currency): Money? {
        if (m.currency == target) return m
        val inRsd = convert(m) ?: return null
        if (target == Currency.RSD) return inRsd
        val rate = toRsd[target] ?: return null
        return Money.of(inRsd.amount.divide(rate, 2, java.math.RoundingMode.HALF_UP), target)
    }

    fun plus(currency: Currency, rate: BigDecimal = BigDecimal.ONE) = Rates(toRsd + (currency to rate))

    companion object {
        val EMPTY = Rates(emptyMap())
    }
}
