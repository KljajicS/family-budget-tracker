package rs.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import rs.budget.data.BudgetRepository
import rs.budget.ui.Routes
import rs.budget.ui.Tab
import rs.budget.ui.add.AddScreen
import rs.budget.ui.budgets.BudgetsScreen
import rs.budget.ui.budgets.EditBudgetScreen
import rs.budget.ui.home.HomeScreen
import rs.budget.ui.insights.InsightsScreen
import rs.budget.ui.settings.AccountsScreen
import rs.budget.ui.settings.CategoriesScreen
import rs.budget.ui.settings.EditAccountScreen
import rs.budget.ui.settings.EditCategoryScreen
import rs.budget.ui.settings.EditRecurringScreen
import rs.budget.ui.settings.RecurringScreen
import rs.budget.ui.settings.SettingsScreen
import rs.budget.ui.setup.SetupScreen
import rs.budget.ui.theme.BodyStyle
import rs.budget.ui.theme.BudgetTheme
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.sans
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BudgetTheme { BudgetRoot() } }
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(repo: BudgetRepository) : ViewModel() {
    val hasMember: StateFlow<Boolean?> = repo.members.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun BudgetRoot() {
    val root: RootViewModel = hiltViewModel()
    val hasMember by root.hasMember.collectAsStateWithLifecycle()
    if (hasMember == null) return
    val c = LocalColors.current
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    Scaffold(
        containerColor = c.bg,
        bottomBar = {
            if (showBar) BottomBar(route) { tab ->
                nav.navigate(tab.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = if (hasMember == true) Tab.HOME.route else Routes.SETUP, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    onAdd = { nav.navigate(Routes.add()) },
                    onBudgets = { nav.navigate(Tab.BUDGETS.route) { launchSingleTop = true } },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onCategories = { nav.navigate(Routes.CATEGORIES) },
                    onAccounts = { nav.navigate(Routes.ACCOUNTS) },
                    onRecurring = { nav.navigate(Routes.RECURRING) },
                )
            }
            composable(Routes.RECURRING) {
                RecurringScreen(onBack = { nav.popBackStack() }, onNew = { nav.navigate(Routes.NEW_RECURRING) }, onEdit = { nav.navigate(Routes.editRecurring(it)) })
            }
            composable(Routes.NEW_RECURRING) { EditRecurringScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.EDIT_RECURRING) { EditRecurringScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { nav.popBackStack() }, onNew = { nav.navigate(Routes.NEW_CATEGORY) }, onEdit = { nav.navigate(Routes.editCategory(it)) })
            }
            composable(Routes.NEW_CATEGORY) { EditCategoryScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.EDIT_CATEGORY) { EditCategoryScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(onBack = { nav.popBackStack() }, onNew = { nav.navigate(Routes.NEW_ACCOUNT) }, onEdit = { nav.navigate(Routes.editAccount(it)) })
            }
            composable(Routes.NEW_ACCOUNT) { EditAccountScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.EDIT_ACCOUNT) { EditAccountScreen(onClose = { nav.popBackStack() }) }
            composable(Tab.INSIGHTS.route) { InsightsScreen() }
            composable(Tab.SCAN.route) { Stub(stringResource(R.string.coming_soon)) }
            composable(Tab.BUDGETS.route) {
                BudgetsScreen(
                    onNew = { nav.navigate(Routes.NEW_BUDGET) },
                    onEdit = { nav.navigate(Routes.editBudget(it)) },
                    onPay = { ruleId, due -> nav.navigate(Routes.addBill(ruleId, due)) },
                )
            }
            composable(Routes.NEW_BUDGET) { EditBudgetScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.EDIT_BUDGET) { EditBudgetScreen(onClose = { nav.popBackStack() }) }
            composable(Tab.GOALS.route) { Stub(stringResource(R.string.coming_soon)) }
            composable(Routes.SETUP) {
                SetupScreen(onDone = { nav.navigate(Tab.HOME.route) { popUpTo(Routes.SETUP) { inclusive = true } } })
            }
            composable(
                Routes.ADD,
                arguments = listOf(
                    navArgument("rule") { nullable = true; defaultValue = null },
                    navArgument("due") { nullable = true; defaultValue = null },
                ),
            ) { AddScreen(onClose = { nav.popBackStack() }) }
        }
    }
}

/** Five equal columns, 84 high, top hairline; Scan is a raised primary circle like the mockup. */
@Composable
private fun BottomBar(route: String?, onSelect: (Tab) -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding()) {
        HorizontalDivider(color = c.border, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(83.dp).padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val active = route == tab.route
                val tint = if (active) c.primary else c.muted
                Column(
                    Modifier.weight(1f).clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (tab == Tab.SCAN) {
                        Box(
                            Modifier.offset(y = (-24).dp).size(56.dp).background(c.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(tab.icon, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(26.dp)) }
                        Text(stringResource(tab.label), style = sans(11, FontWeight.Medium), color = tint, modifier = Modifier.offset(y = (-20).dp))
                    } else {
                        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Text(stringResource(tab.label), style = sans(11, FontWeight.Medium), color = tint)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stub(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, style = BodyStyle, color = LocalColors.current.muted) }
}
