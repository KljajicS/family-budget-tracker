package rs.budget.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import rs.budget.data.StarterCategories
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.ui.add.AddState
import rs.budget.ui.add.AmountInput
import rs.budget.ui.theme.BudgetTheme
import java.time.LocalDate

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun ScanLightPreview() = ScanDesignPreview(false)

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun ScanDarkPreview() = ScanDesignPreview(true)

@Composable
private fun ScanDesignPreview(dark: Boolean) {
    BudgetTheme(dark) {
        Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color(0xFF111315)).systemBarsPadding()) {
        ScanLayout(true, {}) {
            ReceiptReview(AddState(
                amount = AmountInput("4860.00"), date = LocalDate.of(2026, 10, 14),
                accountId = "card", accounts = listOf(Account("card", "Card", Currency.RSD, AccountKind.SPENDING)),
                categories = StarterCategories.all, paidByName = "Partner",
            ), {}, {}, {}, {}, {})
        }
        }
    }
}
