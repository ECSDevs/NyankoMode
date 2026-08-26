package cc.ptoe.nyankomode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cc.ptoe.nyankomode.data.RuleRepository
import cc.ptoe.nyankomode.data.SettingsRepository
import cc.ptoe.nyankomode.data.appDataStore
import cc.ptoe.nyankomode.ui.navigation.AppNavHost
import cc.ptoe.nyankomode.ui.theme.本喵模式Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            本喵模式Theme {
                val context = LocalContext.current
                val ruleRepository = remember { RuleRepository(context.appDataStore) }
                val settingsRepository = remember { SettingsRepository(context.appDataStore) }
                AppNavHost(
                    ruleRepository = ruleRepository,
                    settingsRepository = settingsRepository,
                )
            }
        }
    }
}