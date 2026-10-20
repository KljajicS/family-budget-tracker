package app.kinjar.ui

import app.kinjar.domain.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.YearMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

fun formatTransactionDate(date: LocalDate, today: LocalDate, todayLabel: String, locale: Locale): String {
    if (date == today) return todayLabel
    val pattern = if (date.year in (today.year - 1)..today.year) "d MMM" else "d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, monthLocale(locale)))
}

fun formatDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("d MMM yyyy", monthLocale(locale)))

fun monthName(month: YearMonth, locale: Locale, style: TextStyle = TextStyle.FULL_STANDALONE): String =
    month.month.getDisplayName(style, monthLocale(locale)).replaceFirstChar { it.titlecase(monthLocale(locale)) }.trimEnd('.')
