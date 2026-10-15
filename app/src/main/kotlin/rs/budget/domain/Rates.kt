package rs.budget.domain

import java.math.BigDecimal

class Rates(toRsd: Map<Currency, BigDecimal>) {
    private val toRsd = toRsd.toMap()

    init {
        require(toRsd.all { (currency, rate) -> validRate(rate) && (currency != Currency.RSD || rate.compareTo(BigDecimal.ONE) == 0) })
    }

    fun rate(currency: Currency): BigDecimal? = if (currency == Currency.RSD) BigDecimal.ONE else toRsd[currency]

    fun convert(m: Money): Money? = convert(m, Currency.RSD)

    fun convert(m: Money, target: Currency): Money? {
        if (m.currency == target) return m
        val sourceRate = rate(m.currency) ?: return null
        val targetRate = rate(target) ?: return null
        return Money.of(m.amount.multiply(sourceRate).divide(targetRate, Money.SCALE, java.math.RoundingMode.HALF_UP), target)
    }

    fun plus(currency: Currency, rate: BigDecimal = BigDecimal.ONE) = Rates(toRsd + (currency to rate))

    companion object {
        val EMPTY = Rates(emptyMap())
    }
}

fun validRate(rate: BigDecimal): Boolean =
    rate.scale() in -6..8 && rate.precision() <= 15 && rate.signum() > 0 && rate <= BigDecimal("1000000")

fun parseRate(text: String): BigDecimal? =
    if (text.length <= 16 && Regex("[0-9]{1,7}(\\.[0-9]{1,8})?").matches(text))
        text.toBigDecimalOrNull()?.takeIf(::validRate)
    else null
