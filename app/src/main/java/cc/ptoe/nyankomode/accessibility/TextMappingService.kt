package cc.ptoe.nyankomode.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.RuleRepository
import cc.ptoe.nyankomode.data.SettingsRepository
import cc.ptoe.nyankomode.data.appDataStore
import cc.ptoe.nyankomode.engine.MappingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TextMappingService : AccessibilityService() {

    private val engine = MappingEngine()
    private val rotateState = mutableMapOf<String, Int>() // 轮换计数，仅内存

    @Volatile
    private var rules: List<MappingRule> = emptyList()

    @Volatile
    private var excludedApps: Set<String> = emptySet()

    @Volatile
    private var totalEnabled = true

    private var lastSelfWriteAt = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        val dataStore = applicationContext.appDataStore
        val ruleRepository = RuleRepository(dataStore)
        val settingsRepository = SettingsRepository(dataStore)

        scope.launch {
            ruleRepository.rules.collect { rules = it }
        }
        scope.launch {
            settingsRepository.totalEnabled.collect { totalEnabled = it }
        }
        scope.launch {
            settingsRepository.excludedApps.collect { excludedApps = it }
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        // 防抖：忽略本服务写入引发的回读事件，防死循环
        if (SystemClock.uptimeMillis() - lastSelfWriteAt < 250) return
        if (!totalEnabled) return

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isEmpty() || packageName == this.packageName) return
        if (packageName in excludedApps) return

        val node = event.source ?: return
        if (!node.isEditable) return
        val text = node.text?.firstOrNull()?.toString().orEmpty()
        if (text.isEmpty()) return

        val cursor = if (event.addedCount > 0 && event.fromIndex >= 0) {
            event.fromIndex + event.addedCount
        } else {
            text.length
        }

        val replacement = engine.findReplacement(text, cursor, rules, rotateState) ?: return
        val newText = text.substring(0, replacement.start) +
            replacement.output +
            text.substring(replacement.end)
        if (newText == text) return

        applyReplacement(node, newText, replacement.newCursor)
    }

    private fun applyReplacement(
        node: AccessibilityNodeInfo,
        newText: String,
        cursor: Int,
    ) {
        val setTextBundle = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextBundle)) return

        lastSelfWriteAt = SystemClock.uptimeMillis()

        val selectionBundle = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            selectionBundle
        )
    }
}