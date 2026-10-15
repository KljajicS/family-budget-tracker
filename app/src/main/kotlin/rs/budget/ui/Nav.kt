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
    const val ADD = "add?rule={rule}&due={due}"
    fun add() = "add"
    fun addBill(ruleId: String, due: java.time.LocalDate) = "add?rule=$ruleId&due=$due"
    const val SETTINGS = "settings"
    const val CATEGORIES = "settings/categories"
    const val NEW_CATEGORY = "settings/categories/new"
    const val EDIT_CATEGORY = "settings/categories/edit/{id}"
    fun editCategory(id: String) = "settings/categories/edit/$id"
    const val ACCOUNTS = "settings/accounts"
    const val NEW_ACCOUNT = "settings/accounts/new"
    const val EDIT_ACCOUNT = "settings/accounts/edit/{id}"
    fun editAccount(id: String) = "settings/accounts/edit/$id"
    const val RECURRING = "settings/recurring"
    const val NEW_RECURRING = "settings/recurring/new"
    const val EDIT_RECURRING = "settings/recurring/edit/{id}"
    fun editRecurring(id: String) = "settings/recurring/edit/$id"
    const val NEW_GOAL = "goals/new"
    const val GOAL = "goals/{id}"
    fun goal(id: String) = "goals/$id"
    const val EDIT_GOAL = "goals/{id}/edit"
    fun editGoal(id: String) = "goals/$id/edit"
    const val NEW_BUDGET = "budgets/new"
    const val EDIT_BUDGET = "budgets/edit/{category}"
    fun editBudget(categoryId: String) = "budgets/edit/$categoryId"
}
