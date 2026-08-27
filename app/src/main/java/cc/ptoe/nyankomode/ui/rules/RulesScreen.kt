package cc.ptoe.nyankomode.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.ptoe.nyankomode.R
import cc.ptoe.nyankomode.data.ExecutorType
import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.OutputMode
import cc.ptoe.nyankomode.data.TriggerType
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    rules: List<MappingRule>,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onToggleRuleEnabled: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.rules_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_rule),
                )
            }
        },
    ) { innerPadding ->
        if (rules.isEmpty()) {
            EmptyRules(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rules, key = MappingRule::id) { rule ->
                    RuleCard(
                        rule = rule,
                        onClick = { onEditRule(rule.id) },
                        onToggleEnabled = { onToggleRuleEnabled(rule.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: MappingRule,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                val triggerTypeLabel = stringResource(
                    when (rule.triggerType) {
                        TriggerType.KEYWORD -> R.string.trigger_type_keyword
                        TriggerType.NEW_LINE -> R.string.trigger_type_new_line
                        TriggerType.SEND -> R.string.trigger_type_send
                    },
                )
                Text(
                    text = rule.name.ifBlank { stringResource(R.string.unnamed_rule) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (rule.triggerType == TriggerType.KEYWORD) {
                        stringResource(
                            R.string.rule_summary_keyword,
                            triggerTypeLabel,
                            rule.triggers.joinToString(" / ").ifBlank {
                                stringResource(R.string.empty_value)
                            },
                            rule.outputs.joinToString(" / ").ifBlank {
                                stringResource(R.string.empty_value)
                            },
                        )
                    } else {
                        stringResource(
                            R.string.rule_summary_event,
                            triggerTypeLabel,
                            rule.outputs.joinToString(" / ").ifBlank {
                                stringResource(R.string.empty_value)
                            },
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        if (rule.mode == OutputMode.ROTATE) {
                            R.string.mode_rotate
                        } else {
                            R.string.mode_random
                        },
                    ),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(
                        R.string.executor_type_summary,
                        stringResource(
                            when (rule.executorType) {
                                ExecutorType.REPLACE -> R.string.executor_type_replace
                                ExecutorType.INSERT_BEFORE -> R.string.executor_type_insert_before
                                ExecutorType.INSERT_AFTER -> R.string.executor_type_insert_after
                            },
                        ),
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggleEnabled,
            )
        }
    }
}

@Composable
private fun EmptyRules(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.empty_rules_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.empty_rules_summary),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
