package rs.budget.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import rs.budget.R
import rs.budget.data.FinancialSnapshot
import rs.budget.domain.Currency
import rs.budget.domain.ExchangeRateSnapshot
import rs.budget.ui.theme.CaptionStyle
import rs.budget.ui.theme.LocalColors

data class RateStatus(
    val snapshot: ExchangeRateSnapshot = ExchangeRateSnapshot(),
    val missing: Set<Currency> = emptySet(),
    val stale: Boolean = false,
) {
    companion object {
        fun from(financial: FinancialSnapshot): RateStatus = RateStatus(financial.exchange, financial.missingCurrencies,
            financial.exchange.cached.any { (currency, quote) ->
                currency !in financial.exchange.overrides && !quote.effectiveDate.isAfter(financial.day.minusDays(7))
            })
    }
}

@Composable
fun RateNotice(status: RateStatus) {
    val colors = LocalColors.current
    Column {
        if (status.snapshot.cached.isNotEmpty() || status.snapshot.overrides.isNotEmpty()) {
            Text(stringResource(R.string.rates_estimate), style = CaptionStyle, color = colors.muted)
        }
        if (status.missing.isNotEmpty()) Text(stringResource(R.string.rates_excluded, status.missing.sortedBy { it.ordinal }.joinToString()), style = CaptionStyle, color = colors.orange)
        if (status.stale) Text(stringResource(R.string.rates_stale), style = CaptionStyle, color = colors.orange)
    }
}
