package cc.ptoe.nyankomode.ui.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cc.ptoe.nyankomode.R
import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.ui.components.SegmentedColumn
import cc.ptoe.nyankomode.ui.components.SegmentedColumnItem

@Composable
fun rememberAccessibilityServiceEnabled(): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accessibilityManager = remember {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    val state = remember {
        mutableStateOf(isAccessibilityServiceEnabled(context, accessibilityManager))
    }
    DisposableEffect(lifecycleOwner, accessibilityManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = isAccessibilityServiceEnabled(context, accessibilityManager)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

private fun isAccessibilityServiceEnabled(context: Context, manager: AccessibilityManager?): Boolean {
    if (manager == null) return false
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            info.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                info.resolveInfo?.serviceInfo?.name ==
                "cc.ptoe.nyankomode.accessibility.TextMappingService"
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rules: List<MappingRule>,
    accessibilityEnabled: Boolean,
    totalEnabled: Boolean,
    excludedAppCount: Int,
    onOpenRules: () -> Unit,
    onOpenTrial: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabledRules = rules.count(MappingRule::enabled)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        item {
            StatusCard(
                accessibilityEnabled = accessibilityEnabled,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            )
        }
        item {
            SectionLabel(stringResource(R.string.status_details))
            SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                SegmentedColumnItem(
                    headlineContent = { Text(stringResource(R.string.total_mapping)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (totalEnabled) R.string.setting_enabled else R.string.setting_disabled,
                            ),
                        )
                    },
                    trailingContent = {
                        Text(
                            text = stringResource(
                                if (totalEnabled) R.string.setting_enabled else R.string.setting_disabled,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (totalEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    showDivider = true,
                )
                SegmentedColumnItem(
                    headlineContent = { Text(stringResource(R.string.active_rules)) },
                    supportingContent = {
                        Text(stringResource(R.string.active_rules_value, enabledRules, rules.size))
                    },
                    trailingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    showDivider = true,
                )
                SegmentedColumnItem(
                    headlineContent = { Text(stringResource(R.string.excluded_apps)) },
                    supportingContent = {
                        Text(stringResource(R.string.excluded_apps_value, excludedAppCount))
                    },
                    trailingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                )
            }
        }
        item {
            SectionLabel(stringResource(R.string.home_actions))
            SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                SegmentedColumnItem(
                    headlineContent = { Text(stringResource(R.string.manage_rules)) },
                    supportingContent = {
                        Text(stringResource(R.string.manage_rules_summary, rules.size, enabledRules))
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    onClick = onOpenRules,
                    showDivider = true,
                )
                SegmentedColumnItem(
                    headlineContent = { Text(stringResource(R.string.local_preview)) },
                    supportingContent = { Text(stringResource(R.string.local_preview_summary)) },
                    trailingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = onOpenTrial,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    accessibilityEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val title = stringResource(
        if (accessibilityEnabled) R.string.service_enabled else R.string.service_disabled,
    )
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clickable(onClick = onOpenAccessibilitySettings),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (accessibilityEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.service_status),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(
                    if (accessibilityEnabled) {
                        R.string.service_enabled_summary
                    } else {
                        R.string.service_disabled_summary
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            AssistChip(
                onClick = onOpenAccessibilitySettings,
                label = { Text(stringResource(R.string.open_accessibility_settings)) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 24.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}
