package rs.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.graphics.Color
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import rs.budget.data.rates.RateRefresh
import rs.budget.ui.settings.ExchangeRatesScreen
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
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.isSystemInDarkTheme
import rs.budget.data.BudgetRepository
import rs.budget.data.ThemeMode
import rs.budget.data.ThemeStore
import rs.budget.ui.Routes
import rs.budget.ui.Tab
import rs.budget.ui.add.AddScreen
import rs.budget.ui.scan.ScanScreen
import rs.budget.ui.home.HomeScreen
import rs.budget.ui.goals.EditGoalScreen
import rs.budget.ui.goals.GoalsScreen
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
        setContent {
            val root: RootViewModel = hiltViewModel()
            val mode by root.theme.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BudgetTheme(dark) { BudgetRoot(root) }
        }
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(private val repo: Lazy<BudgetRepository>, themeStore: ThemeStore, private val rateRefresh: Lazy<RateRefresh>) : ViewModel() {
    private val member = MutableStateFlow<Boolean?>(null)
    val hasMember: StateFlow<Boolean?> = member
    private val failure = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = failure
    private var loading = false
    val theme: StateFlow<ThemeMode> = themeStore.mode

    init { load() }

    suspend fun refreshRates() {
        if (member.value != null) withContext(Dispatchers.IO) { rateRefresh.get().refresh() }
    }

    fun load() {
        if (loading || member.value != null) return
        loading = true
        failure.value = false
        viewModelScope.launch {
            try {
                member.value = withContext(Dispatchers.IO) { repo.get().members.first().isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failure.value = true
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun BudgetRoot(root: RootViewModel) {
    val hasMember by root.hasMember.collectAsStateWithLifecycle()
    val failed by root.failed.collectAsStateWithLifecycle()
    if (hasMember == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.startup_failed), color = LocalColors.current.text)
                androidx.compose.material3.TextButton(onClick = root::load) { Text(stringResource(R.string.startup_retry)) }
            } else androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    val c = LocalColors.current
    val nav = rememberNavController()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, root) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                root.refreshRates()
                delay(60_000)
            }
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var scanEditing by remember { mutableStateOf(false) }
    val cameraSurface = route == Tab.SCAN.route && !scanEditing
    val showBar = route != Tab.SCAN.route && Tab.entries.any { it.route == route }
    val activity = LocalActivity.current
    LaunchedEffect(c.dark, cameraSurface) {
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !c.dark && !cameraSurface }
    }

    Scaffold(
        containerColor = if (cameraSurface) (if (c.dark) Color.Black else Color(0xFF111315)) else c.bg,
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
                    onAccounts = { nav.navigate(Tab.ACCOUNTS.route) { launchSingleTop = true } },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onCategories = { nav.navigate(Routes.CATEGORIES) },
                    onAccounts = { nav.navigate(Tab.ACCOUNTS.route) { launchSingleTop = true } },
                    onRecurring = { nav.navigate(Routes.RECURRING) },
                    onRates = { nav.navigate(Routes.EXCHANGE_RATES) },
                )
            }
            composable(Routes.RECURRING) {
                RecurringScreen(
                    onBack = { nav.popBackStack() },
                    onNew = { nav.navigate(Routes.NEW_RECURRING) },
                    onEdit = { nav.navigate(Routes.editRecurring(it)) },
                    onPay = { ruleId, due -> nav.navigate(Routes.addBill(ruleId, due)) },
                )
            }
            composable(Routes.EXCHANGE_RATES) { ExchangeRatesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.NEW_RECURRING) { EditRecurringScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.EDIT_RECURRING) { EditRecurringScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { nav.popBackStack() }, onNew = { nav.navigate(Routes.NEW_CATEGORY) }, onEdit = { nav.navigate(Routes.editCategory(it)) })
            }
            composable(Routes.NEW_CATEGORY) { EditCategoryScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.EDIT_CATEGORY) { EditCategoryScreen(onClose = { nav.popBackStack() }) }
            composable(Tab.ACCOUNTS.route) {
                AccountsScreen(onNew = { nav.navigate(Routes.NEW_ACCOUNT) }, onEdit = { nav.navigate(Routes.editAccount(it)) })
            }
            composable(Routes.NEW_ACCOUNT) { EditAccountScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.EDIT_ACCOUNT) { EditAccountScreen(onClose = { nav.popBackStack() }) }
            composable(Tab.INSIGHTS.route) { InsightsScreen() }
            composable(Tab.SCAN.route) {
                ScanScreen(
                    onEditingChanged = { scanEditing = it },
                    onClose = { nav.popBackStack() },
                    onSaved = { nav.navigate(Tab.HOME.route) { popUpTo(Tab.HOME.route) { inclusive = false }; launchSingleTop = true } },
                    onManual = { nav.navigate(Routes.add()) },
                )
            }
            composable(Tab.GOALS.route) {
                GoalsScreen(onNew = { nav.navigate(Routes.NEW_GOAL) }, onOpen = { nav.navigate(Routes.editGoal(it)) })
            }
            composable(Routes.NEW_GOAL) { EditGoalScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.EDIT_GOAL) { EditGoalScreen(onClose = { nav.popBackStack() }) }
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

@Composable
private fun BottomBar(route: String?, onSelect: (Tab) -> Unit) {
    val c = LocalColors.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(Modifier.matchParentSize().padding(top = 24.dp).background(c.bg)) {
            HorizontalDivider(color = c.border, thickness = 1.dp)
        }
        Row(Modifier.fillMaxWidth().height(104.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
            Tab.entries.forEach { tab ->
                val active = route == tab.route
                val tint = if (active) c.primary else c.muted
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics { selected = active }
                        .clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                ) {
                    if (tab == Tab.SCAN) {
                        Box(
                            Modifier.size(72.dp).background(c.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(tab.icon, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(26.dp)) }
                        Text(stringResource(tab.label), style = sans(11, FontWeight.Medium), color = tint)
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
