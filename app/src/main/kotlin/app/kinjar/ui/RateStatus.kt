package app.kinjar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kinjar.R
import app.kinjar.data.FinancialSnapshot
import app.kinjar.domain.Currency
import app.kinjar.domain.ExchangeRateSnapshot
import app.kinjar.ui.theme.CaptionStyle
import app.kinjar.ui.theme.LocalColors

data class RateStatus(
    val snapshot: ExchangeRateSnapshot = ExchangeRateSnapshot(),
    val missing: Set<Currency> = emptySet(),
    val stale: Boolean = false,
    val estimated: Boolean = false,
) {
    companion object {
        fun from(financial: FinancialSnapshot): RateStatus {
            val currencies = financial.activeAccounts.map { it.currency } + financial.transactions.map { it.money.currency } +
                financial.bills.map { it.rule.money.currency } + financial.goals.map { it.goal.target.currency }
            return RateStatus(financial.exchange, financial.missingCurrencies,
            financial.exchange.cached.any { (currency, quote) ->
                currency !in financial.exchange.overrides && !quote.effectiveDate.isAfter(financial.day.minusDays(7))
            }, estimated = currencies.any { it != Currency.RSD && financial.exchange.rates.rate(it) != null })
        }
    }
}

@Composable
fun RateNotice(status: RateStatus) {
    val colors = LocalColors.current
    Column {
        if (status.estimated) {
            Text(stringResource(R.string.rates_estimate), style = CaptionStyle, color = colors.muted)
        }
        if (status.missing.isNotEmpty()) Text(stringResource(R.string.rates_excluded, status.missing.sortedBy { it.ordinal }.joinToString()), style = CaptionStyle, color = colors.orange)
        if (status.stale) Text(stringResource(R.string.rates_stale), style = CaptionStyle, color = colors.orange)
    }
}
