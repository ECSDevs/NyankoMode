package cc.ptoe.nyankomode.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cc.ptoe.nyankomode.R
import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.OutputMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    rule: MappingRule?,
    onSave: (MappingRule) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember(rule) { mutableStateOf(rule?.name ?: "") }
    var triggers by remember(rule) { mutableStateOf(rule?.triggers ?: emptyList()) }
    var outputs by remember(rule) { mutableStateOf(rule?.outputs ?: emptyList()) }
    var mode by remember(rule) { mutableStateOf(rule?.mode ?: OutputMode.ROTATE) }
    var enabled by remember(rule) { mutableStateOf(rule?.enabled ?: true) }
    val canSave = name.isNotBlank() && triggers.isNotEmpty() && outputs.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(
                        if (rule == null) R.string.create_rule_title else R.string.edit_rule_title,
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.rule_name)) },
                placeholder = { Text(stringResource(R.string.rule_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            StringListEditor(
                title = stringResource(R.string.triggers),
                placeholder = stringResource(R.string.add_trigger_placeholder),
                values = triggers,
                onAdd = { value -> if (value !in triggers) triggers = triggers + value },
                onRemove = { triggers = triggers - it },
            )
            StringListEditor(
                title = stringResource(R.string.outputs),
                placeholder = stringResource(R.string.add_output_placeholder),
                values = outputs,
                onAdd = { value -> if (value !in outputs) outputs = outputs + value },
                onRemove = { outputs = outputs - it },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.output_mode), style = MaterialTheme.typography.titleMedium)
                val segments = listOf(
                    stringResource(R.string.mode_rotate) to OutputMode.ROTATE,
                    stringResource(R.string.mode_random) to OutputMode.RANDOM,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    segments.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            selected = mode == value,
                            onClick = { mode = value },
                            shape = SegmentedButtonDefaults.itemShape(index, segments.size),
                            label = { Text(label) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.enable_this_rule),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Button(
                onClick = {
                    onSave(
                        MappingRule(
                            id = rule?.id ?: System.currentTimeMillis().toString(),
                            name = name.trim(),
                            triggers = triggers.map(String::trim).filter(String::isNotEmpty),
                            outputs = outputs.map(String::trim).filter(String::isNotEmpty),
                            mode = mode,
                            enabled = enabled,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            ) {
                Text(stringResource(R.string.save_rule))
            }
            if (rule != null) {
                TextButton(
                    onClick = { onDelete(rule.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.delete_rule))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StringListEditor(
    title: String,
    placeholder: String,
    values: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    input.trim().takeIf { it.isNotEmpty() && it !in values }?.let(onAdd)
                    input = ""
                },
            ),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(value) },
                    label = { Text(value) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove_value, value),
                        )
                    },
                )
            }
        }
    }
}
