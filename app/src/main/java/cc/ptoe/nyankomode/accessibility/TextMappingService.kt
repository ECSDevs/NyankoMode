package cc.ptoe.nyankomode.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.RuleRepository
import cc.ptoe.nyankomode.data.SettingsRepository
import cc.ptoe.nyankomode.data.TriggerType
import cc.ptoe.nyankomode.data.appDataStore
import cc.ptoe.nyankomode.engine.MappingEngine
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TextMappingService : AccessibilityService() {

    private val engine = MappingEngine()
    private val rotateState = mutableMapOf<String, Int>() // 轮换计数，仅内存

    @Volatile
    private var ruleSnapshot = RuleSnapshot()

    @Volatile
    private var excludedApps: Set<String> = emptySet()

    @Volatile
    private var totalEnabled = true

    private var lastSelfWriteAt = 0L
    private lateinit var windowManager: WindowManager
    private val displayMetrics = DisplayMetrics()
    private var blockerRects: List<Rect> = emptyList()
    private val blockerViews = mutableListOf<TouchInterceptorView>()
    private var overlayAttached = false
    private var overlayUpdatePosted = false
    private var replayInProgress = false
    private var suppressReplayedSendUntil = 0L
    private var activeInput: AccessibilityNodeInfo? = null
    private var activeInputPackage: String = ""
    private val overlayFrameCallback = Choreographer.FrameCallback {
        overlayUpdatePosted = false
        updateOverlay()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)

        val dataStore = applicationContext.appDataStore
        val ruleRepository = RuleRepository(dataStore)
        val settingsRepository = SettingsRepository(dataStore)

        scope.launch {
            ruleRepository.rules.collect { loadedRules ->
                val usableRules = loadedRules.filter {
                    it.enabled && it.id.isNotEmpty() && it.outputs.isNotEmpty()
                }
                ruleSnapshot = RuleSnapshot(
                    keyword = usableRules.filter { it.triggerType == TriggerType.KEYWORD },
                    newLine = usableRules.filter { it.triggerType == TriggerType.NEW_LINE },
                    send = usableRules.filter { it.triggerType == TriggerType.SEND },
                )
            }
        }
        scope.launch {
            settingsRepository.totalEnabled.collect {
                totalEnabled = it
                if (!it) clearActiveInput()
                scheduleOverlayUpdate()
            }
        }
        scope.launch {
            settingsRepository.excludedApps.collect {
                excludedApps = it
                if (activeInputPackage in it) clearActiveInput()
                scheduleOverlayUpdate()
            }
        }
        scheduleOverlayUpdate()
    }

    override fun onInterrupt() {
        replayInProgress = false
        clearActiveInput()
        removeOverlay()
    }

    override fun onDestroy() {
        replayInProgress = false
        if (overlayUpdatePosted) {
            Choreographer.getInstance().removeFrameCallback(overlayFrameCallback)
            overlayUpdatePosted = false
        }
        clearActiveInput()
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val eventPackage = event.packageName?.toString().orEmpty()
                if (eventPackage.isNotEmpty() && eventPackage != activeInputPackage) {
                    clearActiveInput()
                }
                scheduleOverlayUpdate()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> scheduleOverlayUpdate()
        }
        if (shouldIgnore(event)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                event.source?.takeIf { it.isWritableTextInput() }?.let(::rememberActiveInput)
                scheduleOverlayUpdate()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val changed = event.source
                    ?.takeIf { it.isWritableTextInput() }
                    ?.let(::rememberActiveInput) == true
                if (changed) scheduleOverlayUpdate()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged(event)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (!isReplayedSendEvent()) handleSendClicked(event)
            }
        }
    }

    private fun isReplayedSendEvent(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now >= suppressReplayedSendUntil) return false
        suppressReplayedSendUntil = 0L
        return true
    }

    private fun rememberActiveInput(node: AccessibilityNodeInfo): Boolean {
        val changed = activeInput != node
        if (changed) {
            activeInput?.recycle()
            activeInput = AccessibilityNodeInfo.obtain(node)
        }
        activeInputPackage = node.packageName?.toString().orEmpty()
        return changed
    }
    private fun clearActiveInput() {
        activeInput?.recycle()
        activeInput = null
        activeInputPackage = ""
    }

    private fun AccessibilityNodeInfo.isWritableTextInput(): Boolean =
        isEditable && actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }

    private fun scheduleOverlayUpdate() {
        if (overlayUpdatePosted) return
        overlayUpdatePosted = true
        Choreographer.getInstance().postFrameCallback(overlayFrameCallback)
    }

    private fun shouldIgnore(event: AccessibilityEvent): Boolean {
        // 仅忽略本服务写入引发的文本回读；焦点、点击和窗口事件仍需及时处理。
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            SystemClock.uptimeMillis() - lastSelfWriteAt < SELF_WRITE_DEBOUNCE_MS
        ) return true
        if (!totalEnabled) return true

        val sourcePackage = event.packageName?.toString().orEmpty()
        return sourcePackage.isEmpty() || sourcePackage == packageName || sourcePackage in excludedApps
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        val node = event.source ?: return
        if (!node.isWritableTextInput()) return
        if (rememberActiveInput(node)) scheduleOverlayUpdate()

        val text = node.text?.toString().orEmpty()
        if (text.isEmpty()) return
        val cursor = node.getTextSelectionEnd().takeIf { it in 0..text.length }
            ?: if (event.addedCount > 0 && event.fromIndex >= 0) {
                (event.fromIndex + event.addedCount).coerceIn(0, text.length)
            } else {
                text.length
            }
        val triggerType = if (event.addedCount > 0 && cursor > 0 && text[cursor - 1] == '\n') {
            TriggerType.NEW_LINE
        } else {
            TriggerType.KEYWORD
        }
        val replacement = engine.findReplacement(
            text = text,
            cursor = cursor,
            rules = ruleSnapshot.forType(triggerType),
            rotateState = rotateState,
            triggerType = triggerType,
        ) ?: return
        val newText = buildMappedText(text, replacement.start, replacement.end, replacement.output)
        if (newText == text) return
        applyReplacement(node, newText, replacement.newCursor)
    }


    private fun handleSendClicked(event: AccessibilityEvent) {
        if (overlayAttached) return
        val sendControl = event.source ?: return
        if (!sendControl.isSendControl()) return
        applySendRule()
    }

    private fun applySendRule(): Boolean {
        val node = activeInput ?: return false
        if (!node.refresh()) {
            clearActiveInput()
            scheduleOverlayUpdate()
            return false
        }
        val text = node.text?.toString().orEmpty()
        if (text.isEmpty()) return false
        val cursor = node.getTextSelectionEnd().takeIf { it in 0..text.length } ?: text.length
        val replacement = engine.findReplacement(
            text = text,
            cursor = cursor,
            rules = ruleSnapshot.send,
            rotateState = rotateState,
            triggerType = TriggerType.SEND,
        ) ?: return false
        val newText = buildMappedText(text, replacement.start, replacement.end, replacement.output)
        if (newText == text) return false
        return applyReplacement(node, newText, replacement.newCursor)
    }

    private fun updateOverlay() {
        if (replayInProgress) return
        if (!::windowManager.isInitialized || !totalEnabled ||
            activeInput == null || activeInputPackage.isEmpty() ||
            activeInputPackage == packageName || activeInputPackage in excludedApps
        ) {
            removeOverlay()
            return
        }

        val input = activeInput ?: return
        if (!input.refresh()) {
            clearActiveInput()
            removeOverlay()
            return
        }
        val screen = screenBounds()
        val inputBounds = Rect().also(input::getBoundsInScreen)
        if (!inputBounds.intersect(screen) || inputBounds.isEmpty) {
            removeOverlay()
            return
        }
        val passThroughRects = mutableListOf(inputBounds)
        imeBounds()?.also { ime ->
            if (ime.intersect(screen) && !ime.isEmpty) passThroughRects += ime
        }
        applyBlockerRects(buildBlockerRects(screen, passThroughRects))
    }

    private fun applyBlockerRects(newRects: List<Rect>) {
        if (newRects == blockerRects) return
        runCatching {
            val sharedCount = minOf(blockerViews.size, newRects.size)
            for (index in 0 until sharedCount) {
                val rect = newRects[index]
                if (rect != blockerRects[index]) {
                    val view = blockerViews[index]
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.setBounds(rect)
                    windowManager.updateViewLayout(view, params)
                }
            }
            while (blockerViews.size > newRects.size) {
                val lastIndex = blockerViews.lastIndex
                windowManager.removeView(blockerViews.removeAt(lastIndex))
            }
            for (index in blockerViews.size until newRects.size) {
                TouchInterceptorView().also { view ->
                    windowManager.addView(view, blockerParams(newRects[index]))
                    blockerViews += view
                }
            }
            blockerRects = newRects.map(::Rect)
            overlayAttached = blockerViews.isNotEmpty()
        }.onFailure { removeOverlay() }
    }

    private fun imeBounds(): Rect? {
        val bounds = Rect()
        val current = Rect()
        var found = false
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val root = window.root ?: continue
            root.getBoundsInScreen(current)
            root.recycle()
            if (current.isEmpty) continue
            if (found) bounds.union(current) else bounds.set(current)
            found = true
        }
        return bounds.takeIf { found }
    }

    private fun buildBlockerRects(screen: Rect, passThroughRects: List<Rect>): List<Rect> {
        if (screen.isEmpty) return emptyList()
        val xEdges = mutableListOf(screen.left, screen.right)
        val yEdges = mutableListOf(screen.top, screen.bottom)
        for (rect in passThroughRects) {
            xEdges += rect.left.coerceIn(screen.left, screen.right)
            xEdges += rect.right.coerceIn(screen.left, screen.right)
            yEdges += rect.top.coerceIn(screen.top, screen.bottom)
            yEdges += rect.bottom.coerceIn(screen.top, screen.bottom)
        }
        val sortedX = xEdges.distinct().sorted()
        val sortedY = yEdges.distinct().sorted()
        val result = mutableListOf<Rect>()
        var activeRuns = emptyMap<Long, Rect>()

        for (yIndex in 0 until sortedY.lastIndex) {
            val top = sortedY[yIndex]
            val bottom = sortedY[yIndex + 1]
            if (bottom <= top) continue
            val centerY = (top + bottom) / 2
            val nextRuns = mutableMapOf<Long, Rect>()
            var runLeft: Int? = null

            fun appendRun(left: Int, right: Int) {
                if (right <= left) return
                val key = (left.toLong() shl 32) xor (right.toLong() and 0xffffffffL)
                val previous = activeRuns[key]
                val rect = if (previous != null && previous.bottom == top) {
                    previous.apply { this.bottom = bottom }
                } else {
                    Rect(left, top, right, bottom).also(result::add)
                }
                nextRuns[key] = rect
            }

            for (xIndex in 0 until sortedX.lastIndex) {
                val left = sortedX[xIndex]
                val right = sortedX[xIndex + 1]
                val centerX = (left + right) / 2
                val blocked = right > left && passThroughRects.none { it.contains(centerX, centerY) }
                if (blocked) {
                    if (runLeft == null) runLeft = left
                } else if (runLeft != null) {
                    appendRun(runLeft, left)
                    runLeft = null
                }
            }
            runLeft?.let { appendRun(it, sortedX.last()) }
            activeRuns = nextRuns
        }
        return result
    }




    private fun blockerParams(rect: Rect) = WindowManager.LayoutParams(
        rect.width(),
        rect.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        setBounds(rect)
        title = "${packageName}: input touch guard"
    }

    private fun WindowManager.LayoutParams.setBounds(rect: Rect) {
        width = rect.width()
        height = rect.height()
        x = rect.left
        y = rect.top
    }

    @Suppress("DEPRECATION")
    private fun screenBounds(): Rect {
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        return Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
    private fun removeOverlay() {
        for (index in blockerViews.lastIndex downTo 0) {
            runCatching { windowManager.removeView(blockerViews[index]) }
        }
        blockerViews.clear()
        blockerRects = emptyList()
        overlayAttached = false
    }

    private fun onInterceptedTouch(points: List<TouchPoint>) {
        if (points.isEmpty()) return
        val first = points.first()
        val root = rootInActiveWindow
        val sendHit = root?.let { findSendControlAt(it, first.x, first.y) }
        if (root != null && root !== sendHit) root.recycle()
        if (sendHit != null) applySendRule()

        // The injected gesture must reach the target app, not this overlay again.
        removeOverlay()
        if (sendHit != null) {
            suppressReplayedSendUntil = SystemClock.uptimeMillis() + REPLAYED_SEND_SUPPRESSION_MS
            if (sendHit.refresh() && sendHit.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                sendHit.recycle()
                scheduleOverlayUpdate()
                return
            }
            suppressReplayedSendUntil = 0L
        }
        sendHit?.recycle()

        replayInProgress = true
        val dispatched = replayTouch(points)
        if (dispatched && sendHit != null) {
            suppressReplayedSendUntil = SystemClock.uptimeMillis() + REPLAYED_SEND_SUPPRESSION_MS
        } else if (!dispatched) {
            replayInProgress = false
            scheduleOverlayUpdate()
        }
    }
    private fun replayTouch(points: List<TouchPoint>): Boolean {
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
        }
        val duration = (points.last().time - points.first().time).coerceAtLeast(1L)
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(
                    path,
                    0,
                    duration,
                )
            )
            .build()
        return dispatchGesture(
            gesture,
            object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription) {
                    replayInProgress = false
                    scheduleOverlayUpdate()
                }

                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription) {
                    replayInProgress = false
                    scheduleOverlayUpdate()
                }
            },
            null,
        )
    }



    private fun findSendControlAt(
        node: AccessibilityNodeInfo,
        x: Float,
        y: Float,
    ): AccessibilityNodeInfo? = findSendControlAt(node, x.toInt(), y.toInt(), Rect())

    private fun findSendControlAt(
        node: AccessibilityNodeInfo,
        x: Int,
        y: Int,
        bounds: Rect,
    ): AccessibilityNodeInfo? {
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) return null
        for (index in node.childCount - 1 downTo 0) {
            val child = node.getChild(index) ?: continue
            val match = findSendControlAt(child, x, y, bounds)
            if (match != null) {
                if (match !== child) child.recycle()
                return match
            }
            child.recycle()
        }
        return node.takeIf { it.isSendControl() }
    }

    private fun AccessibilityNodeInfo.isSendControl(): Boolean =
        isClickable && (
            text.isSendLabel() ||
                contentDescription.isSendLabel() ||
                viewIdResourceName.isSendLabel() ||
                className.isSendLabel()
            )

    private fun CharSequence?.isSendLabel(): Boolean {
        val label = this?.toString()?.lowercase(Locale.ROOT) ?: return false
        val resourceName = label.substringAfterLast('/').substringAfterLast(':')
        return label == "send" ||
            resourceName == "send" ||
            label.contains("send message") ||
            label.contains("send_button") ||
            label.contains("sendbutton") ||
            label.contains("submit") ||
            label.contains("发送")
    }

    private fun buildMappedText(text: String, start: Int, end: Int, output: String): String =
        buildString(text.length - (end - start) + output.length) {
            append(text, 0, start)
            append(output)
            append(text, end, text.length)
        }

    private fun applyReplacement(
        node: AccessibilityNodeInfo,
        newText: String,
        cursor: Int,
    ): Boolean {
        val setTextBundle = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextBundle)) return false

        lastSelfWriteAt = SystemClock.uptimeMillis()

        val selectionBundle = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionBundle)
        return true
    }

    private data class RuleSnapshot(
        val keyword: List<MappingRule> = emptyList(),
        val newLine: List<MappingRule> = emptyList(),
        val send: List<MappingRule> = emptyList(),
    ) {
        fun forType(triggerType: TriggerType): List<MappingRule> = when (triggerType) {
            TriggerType.KEYWORD -> keyword
            TriggerType.NEW_LINE -> newLine
            TriggerType.SEND -> send
        }
    }

    private data class TouchPoint(val x: Float, val y: Float, val time: Long)

    @SuppressLint("ViewConstructor")
    private inner class TouchInterceptorView : View(this@TextMappingService) {
        private val points = mutableListOf<TouchPoint>()
        private var multiTouch = false

        init {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    points.clear()
                    multiTouch = false
                    addPoint(event, force = true)
                }
                MotionEvent.ACTION_MOVE -> if (!multiTouch) addPoint(event)
                MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
                MotionEvent.ACTION_UP -> {
                    if (!multiTouch) {
                        addPoint(event, force = true)
                        onInterceptedTouch(points)
                    }
                    points.clear()
                }
                MotionEvent.ACTION_CANCEL -> points.clear()
            }
            return true
        }

        private fun addPoint(event: MotionEvent, force: Boolean = false) {
            val last = points.lastOrNull()
            if (!force && last != null) {
                val dx = event.rawX - last.x
                val dy = event.rawY - last.y
                if (event.eventTime - last.time < TOUCH_SAMPLE_INTERVAL_MS &&
                    dx * dx + dy * dy < TOUCH_SAMPLE_DISTANCE_SQUARED
                ) return
            }
            if (points.size >= MAX_TOUCH_POINTS) compactPoints()
            points += TouchPoint(event.rawX, event.rawY, event.eventTime)
        }

        private fun compactPoints() {
            var writeIndex = 1
            for (readIndex in 2 until points.size step 2) {
                points[writeIndex++] = points[readIndex]
            }
            points.subList(writeIndex, points.size).clear()
        }
    }

    private companion object {
        const val SELF_WRITE_DEBOUNCE_MS = 250L
        const val REPLAYED_SEND_SUPPRESSION_MS = 1_000L
        const val TOUCH_SAMPLE_INTERVAL_MS = 16L
        const val TOUCH_SAMPLE_DISTANCE_SQUARED = 16f
        const val MAX_TOUCH_POINTS = 512
    }
}