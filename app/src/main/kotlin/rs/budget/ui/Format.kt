package rs.budget.ui

import rs.budget.domain.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

fun formatAmount(m: Money, sign: Boolean = false, locale: Locale = Locale.getDefault()): String {
    val abs = m.amount.abs()
    val pattern = if (abs.stripTrailingZeros().scale() <= 0) "#,##0" else "#,##0.00"
    val body = DecimalFormat(pattern, DecimalFormatSymbols(locale)).format(abs)
    return when {
        m.isNegative -> "-$body"
        sign && !m.isZero -> "+$body"
        else -> body
    }
}

fun formatMoneyNumber(amount: BigDecimal, locale: Locale = Locale.getDefault()): String =
    DecimalFormat("#,##0.##", DecimalFormatSymbols(locale)).format(amount)
