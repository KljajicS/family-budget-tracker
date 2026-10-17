package rs.budget.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.Currency
import rs.budget.domain.parseRate
import rs.budget.ui.WriteFeedback
import rs.budget.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExchangeRatesScreen(onBack: () -> Unit, vm: ExchangeRatesViewModel = hiltViewModel()) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val failed by vm.loadFailed.collectAsStateWithLifecycle()
    val drafts by vm.inputs.collectAsStateWithLifecycle()
    val refresh by vm.refreshState.collectAsStateWithLifecycle()
    val writes by vm.writes.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    Column(Modifier.fillMaxSize().background(c.bg).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(stringResource(R.string.rates_title), onBack)
        if (failed) LoadFailedBanner(vm::retryLoad)
        Text(stringResource(R.string.rates_description), style = CaptionStyle, color = c.muted)
        PrimaryButton(stringResource(if (refresh.busy) R.string.rates_refreshing else R.string.rates_refresh), enabled = !refresh.busy, onClick = vm::refresh)
        if (refresh.failed) Text(stringResource(R.string.rates_refresh_failed), color = c.orange, style = CaptionStyle)
        WriteFeedback(vm.writes)
        val current = snapshot ?: return@Column
        for (currency in listOf(Currency.EUR, Currency.USD)) {
            OutlinedBox {
                val manual = current.overrides[currency]
                val quote = current.cached[currency]
                val rate = current.rates.rate(currency)
                Text(currency.name, style = TitleStyle, color = c.text)
                Text(if (rate == null) stringResource(R.string.goals_no_rate) else "1 $currency = ${rate.stripTrailingZeros().toPlainString()} RSD", style = BodyStyle, color = c.text)
                Text(stringResource(if (manual != null) R.string.rates_manual else R.string.rates_nbs), style = CaptionStyle, color = c.muted)
                if (quote != null) {
                    Text(stringResource(R.string.rates_effective, quote.effectiveDate.toString()), style = CaptionStyle, color = c.muted)
                    Text(stringResource(R.string.rates_fetched, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(quote.fetchedAt)), style = CaptionStyle, color = c.muted)
                }
                val draft = drafts[currency].orEmpty()
                FieldBox(stringResource(R.string.rates_input, currency.name), draft, { vm.setInput(currency, it) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                PrimaryButton(stringResource(R.string.rates_save), enabled = !writes.busy && parseRate(draft.trim().replace(',', '.')) != null, onClick = { vm.save(currency) })
                if (manual != null) TextButton(onClick = { vm.reset(currency) }, enabled = !writes.busy) { Text(stringResource(R.string.rates_reset)) }
            }
        }
    }
}
