package cc.ptoe.nyankomode.ui.trial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.ptoe.nyankomode.R
import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.TriggerType
import cc.ptoe.nyankomode.engine.MappingEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrialScreen(
    rules: List<MappingRule>,
    onBack: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var explicitTriggerType by remember { mutableStateOf<TriggerType?>(null) }
    val engine = remember { MappingEngine() }
    val preview = remember(input, rules, explicitTriggerType) {
        val enabledRules = rules.filter(MappingRule::enabled)
        val rotateState = mutableMapOf<String, Int>()
        var text = engine.simulateTyping(input, enabledRules, rotateState)
        if (explicitTriggerType == TriggerType.SEND) {
            val replacement = engine.findReplacement(
                text = text,
                cursor = text.length,
                rules = enabledRules,
                rotateState = rotateState,
                triggerType = TriggerType.SEND,
            )
            if (replacement != null) {
                text = text.replaceRange(replacement.start, replacement.end, replacement.output)
            }
        }
        text
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.preview_title)) },
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
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.preview_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    explicitTriggerType = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.preview_input_hint)) },
                minLines = 3,
            )
            Button(
                onClick = { explicitTriggerType = TriggerType.SEND },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.preview_send))
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.preview_result),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = preview, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(1.dp))
        }
    }
}
