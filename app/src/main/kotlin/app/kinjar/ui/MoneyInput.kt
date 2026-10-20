package app.kinjar.ui

import app.kinjar.domain.Currency
import app.kinjar.domain.Money

private val moneyPattern = Regex("[0-9]{1,12}(\\.[0-9]{0,2})?")

/** Accepts a comma decimal separator because Serbian decimal keyboards only offer a comma. */
fun parseMoneyInput(text: String, currency: Currency): Money? {
    val normalized = text.trim().replace(',', '.')
    return if (normalized.length <= 15 && moneyPattern.matches(normalized)) Money.of(normalized, currency) else null
}
