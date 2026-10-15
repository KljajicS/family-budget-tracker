package rs.budget.ui

import rs.budget.domain.Currency
import rs.budget.domain.Money

private val moneyPattern = Regex("[0-9]{1,12}(\\.[0-9]{0,2})?")

fun parseMoneyInput(text: String, currency: Currency): Money? =
    if (text.length <= 15 && moneyPattern.matches(text)) Money.of(text, currency) else null
