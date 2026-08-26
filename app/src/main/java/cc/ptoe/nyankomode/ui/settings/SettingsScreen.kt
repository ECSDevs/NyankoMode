package cc.ptoe.nyankomode.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.ptoe.nyankomode.R
import cc.ptoe.nyankomode.ui.components.SegmentedColumn
import cc.ptoe.nyankomode.ui.components.SegmentedColumnItem

private fun loadAppLabel(context: Context, packageName: String): String {
    return runCatching { context.packageManager.getApplicationInfo(packageName, 0) }
        .getOrNull()
        ?.let { context.packageManager.getApplicationLabel(it).toString() }
        ?: packageName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    totalEnabled: Boolean,
    excludedApps: Set<String>,
    onToggleTotal: (Boolean) -> Unit,
    onAddExcludedApp: (String) -> Unit,
    onRemoveExcludedApp: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val sortedExcludedApps = excludedApps.sorted()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        }
        item {
            SectionLabel(stringResource(R.string.mapping_section))
            SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                SegmentedColumnItem(
                    headlineContent = { Text(stringResource(R.string.total_mapping)) },
                    supportingContent = { Text(stringResource(R.string.total_mapping_summary)) },
                    trailingContent = {
                        Switch(
                            checked = totalEnabled,
                            onCheckedChange = onToggleTotal,
                        )
                    },
                    showDivider = true,
                )
                SegmentedColumnItem(
                    headlineContent = { Text(stringResource(R.string.accessibility_service)) },
                    supportingContent = { Text(stringResource(R.string.accessibility_service_summary)) },
                    trailingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = onOpenAccessibilitySettings,
                )
            }
        }
        item {
            SectionLabel(stringResource(R.string.app_exclusions_section))
            SegmentedColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (sortedExcludedApps.isEmpty()) {
                    SegmentedColumnItem(
                        headlineContent = { Text(stringResource(R.string.no_excluded_apps)) },
                        supportingContent = { Text(stringResource(R.string.no_excluded_apps_summary)) },
                    )
                } else {
                    sortedExcludedApps.forEachIndexed { index, packageName ->
                        val appLabel = remember(context, packageName) {
                            loadAppLabel(context, packageName)
                        }
                        SegmentedColumnItem(
                            headlineContent = { Text(appLabel) },
                            supportingContent = { Text(packageName) },
                            trailingContent = {
                                IconButton(onClick = { onRemoveExcludedApp(packageName) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(
                                            R.string.remove_excluded_app,
                                            appLabel,
                                        ),
                                    )
                                }
                            },
                            showDivider = index < sortedExcludedApps.lastIndex,
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { showDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.add_excluded_app))
            }
        }
    }

    if (showDialog) {
        val launcherApps = remember(context) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            context.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .sorted()
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.excluded_apps_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.excluded_apps_dialog_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(launcherApps, key = { it }) { packageName ->
                            val appLabel = remember(context, packageName) {
                                loadAppLabel(context, packageName)
                            }
                            TextButton(
                                onClick = {
                                    onAddExcludedApp(packageName)
                                    showDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Text(appLabel, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
