package cc.ptoe.nyankomode.ui.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cc.ptoe.nyankomode.R
import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.RuleRepository
import cc.ptoe.nyankomode.data.SettingsRepository
import cc.ptoe.nyankomode.ui.editor.RuleEditorScreen
import cc.ptoe.nyankomode.ui.home.HomeScreen
import cc.ptoe.nyankomode.ui.home.rememberAccessibilityServiceEnabled
import cc.ptoe.nyankomode.ui.rules.RulesScreen
import cc.ptoe.nyankomode.ui.settings.SettingsScreen
import cc.ptoe.nyankomode.ui.trial.TrialScreen
import kotlinx.coroutines.launch

object AppRoutes {
    const val HOME = "home"
    const val RULES = "rules"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    const val TRIAL = "trial"
    const val EDITOR_ARG = "ruleId"

    fun editor(ruleId: String? = null): String =
        if (ruleId == null) EDITOR else "$EDITOR?$EDITOR_ARG=$ruleId"
}

@Composable
fun AppNavHost(
    ruleRepository: RuleRepository,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val rules by ruleRepository.rules.collectAsState(initial = emptyList<MappingRule>())
    val totalEnabled by settingsRepository.totalEnabled.collectAsState(initial = true)
    val excludedApps by settingsRepository.excludedApps.collectAsState(initial = emptySet<String>())
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showNavigation = currentRoute in setOf(AppRoutes.HOME, AppRoutes.RULES, AppRoutes.SETTINGS)

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showNavigation) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == AppRoutes.HOME,
                        onClick = { navController.navigateToTopLevel(AppRoutes.HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == AppRoutes.RULES,
                        onClick = { navController.navigateToTopLevel(AppRoutes.RULES) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_rules)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == AppRoutes.SETTINGS,
                        onClick = { navController.navigateToTopLevel(AppRoutes.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppRoutes.HOME) {
                HomeScreen(
                    rules = rules,
                    accessibilityEnabled = rememberAccessibilityServiceEnabled().value,
                    totalEnabled = totalEnabled,
                    excludedAppCount = excludedApps.size,
                    onOpenRules = { navController.navigateToTopLevel(AppRoutes.RULES) },
                    onOpenTrial = { navController.navigate(AppRoutes.TRIAL) },
                    onOpenAccessibilitySettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
            composable(AppRoutes.RULES) {
                RulesScreen(
                    rules = rules,
                    onAddRule = { navController.navigate(AppRoutes.editor()) },
                    onEditRule = { navController.navigate(AppRoutes.editor(it)) },
                    onToggleRuleEnabled = { id, enabled ->
                        scope.launch { ruleRepository.setEnabled(id, enabled) }
                    },
                )
            }
            composable(AppRoutes.SETTINGS) {
                SettingsScreen(
                    totalEnabled = totalEnabled,
                    excludedApps = excludedApps,
                    onToggleTotal = { enabled ->
                        scope.launch { settingsRepository.setTotalEnabled(enabled) }
                    },
                    onAddExcludedApp = { packageName ->
                        scope.launch { settingsRepository.setExcludedApps(excludedApps + packageName) }
                    },
                    onRemoveExcludedApp = { packageName ->
                        scope.launch { settingsRepository.setExcludedApps(excludedApps - packageName) }
                    },
                    onOpenAccessibilitySettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
            composable(
                route = "${AppRoutes.EDITOR}?${AppRoutes.EDITOR_ARG}={${AppRoutes.EDITOR_ARG}}",
                arguments = listOf(
                    navArgument(AppRoutes.EDITOR_ARG) {
                        type = NavType.StringType
                        defaultValue = null
                        nullable = true
                    },
                ),
            ) { backStackEntry ->
                val ruleId = backStackEntry.arguments?.getString(AppRoutes.EDITOR_ARG)
                val rule = rules.firstOrNull { it.id == ruleId }
                RuleEditorScreen(
                    rule = rule,
                    onSave = { saved ->
                        scope.launch {
                            ruleRepository.upsert(saved)
                            navController.popBackStack()
                        }
                    },
                    onDelete = { id ->
                        scope.launch {
                            ruleRepository.delete(id)
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppRoutes.TRIAL) {
                TrialScreen(
                    rules = rules,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
