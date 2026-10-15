package rs.budget.ui

import rs.budget.domain.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.YearMonth
import java.time.format.TextStyle
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

fun formatCompact(m: Money, locale: Locale = Locale.getDefault()): String {
    val abs = m.amount.abs()
    if (abs < BigDecimal(1000)) return formatAmount(m, locale = locale)
    val k = abs.divide(BigDecimal(1000), 1, java.math.RoundingMode.HALF_UP)
    val body = DecimalFormat("#,##0.#", DecimalFormatSymbols(locale)).format(k) + "k"
    return if (m.isNegative) "-$body" else body
}

fun monthLocale(locale: Locale): Locale = if (locale.language == "sr") Locale.forLanguageTag("sr-Latn") else locale

fun monthName(month: YearMonth, locale: Locale, style: TextStyle = TextStyle.FULL_STANDALONE): String =
    month.month.getDisplayName(style, monthLocale(locale)).replaceFirstChar { it.titlecase(monthLocale(locale)) }.trimEnd('.')
