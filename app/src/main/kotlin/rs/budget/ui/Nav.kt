package rs.budget.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector
import rs.budget.R

enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    INSIGHTS("insights", R.string.tab_insights, Icons.Outlined.BarChart),
    SCAN("scan", R.string.tab_scan, Icons.Outlined.QrCodeScanner),
    BUDGETS("budgets", R.string.tab_budgets, Icons.Outlined.AccountBalanceWallet),
    GOALS("goals", R.string.tab_goals, Icons.Outlined.TrackChanges),
}

object Routes {
    const val SETUP = "setup"
    const val ADD = "add"
    const val EDIT_BUDGETS = "budgets/edit"
}
