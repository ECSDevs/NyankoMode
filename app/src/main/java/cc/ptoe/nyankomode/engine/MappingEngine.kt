package cc.ptoe.nyankomode.engine

import cc.ptoe.nyankomode.data.ExecutorType
import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.OutputMode
import cc.ptoe.nyankomode.data.TriggerType
import kotlin.random.Random

/** 一次命中的替换结果 */
data class Replacement(
    val ruleId: String,
    val output: String,
    val start: Int,
    val end: Int,
    /** 替换后应将光标移动到的位置 */
    val newCursor: Int,
)

class MappingEngine(private val random: Random = Random.Default) {

    fun findReplacement(
        text: CharSequence,
        cursor: Int,
        rules: List<MappingRule>,
        rotateState: MutableMap<String, Int> = mutableMapOf(),
        triggerType: TriggerType = TriggerType.KEYWORD,
    ): Replacement? = when (triggerType) {
        TriggerType.KEYWORD -> findKeywordReplacement(text, cursor, rules, rotateState)
        TriggerType.NEW_LINE -> findNewLineReplacement(text, cursor, rules, rotateState)
        TriggerType.SEND -> findSendReplacement(text, cursor, rules, rotateState)
    }

    /** 按逐字输入时的文本变化顺序应用关键词和换行规则，供本地预览复用。 */
    fun simulateTyping(
        input: CharSequence,
        rules: List<MappingRule>,
        rotateState: MutableMap<String, Int> = mutableMapOf(),
    ): String {
        val text = StringBuilder(input.length)
        for (character in input) {
            text.append(character)
            val triggerType = if (character == '\n') TriggerType.NEW_LINE else TriggerType.KEYWORD
            val replacement = findReplacement(text, text.length, rules, rotateState, triggerType) ?: continue
            text.replace(replacement.start, replacement.end, replacement.output)
        }
        return text.toString()
    }

    private fun findKeywordReplacement(
        text: CharSequence,
        cursor: Int,
        rules: List<MappingRule>,
        rotateState: MutableMap<String, Int>,
    ): Replacement? {
        if (text.isEmpty() || cursor !in 0..text.length) return null

        val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        var bestRule: MappingRule? = null
        var bestTrigger: String? = null
        var bestStart = -1
        var bestDistance = Int.MAX_VALUE

        for (rule in rules) {
            if (!rule.isUsable(TriggerType.KEYWORD)) continue
            for (trigger in rule.triggers) {
                if (trigger.isEmpty() || trigger.length > lineEnd - lineStart) continue
                val maxStart = lineEnd - trigger.length
                val targetStart = cursor - trigger.length
                val leftStart = targetStart.coerceIn(lineStart, maxStart)
                val leftMatch = text.lastIndexOf(trigger, leftStart, ignoreCase = false)
                    .takeIf { it >= lineStart }
                if (leftMatch != null) {
                    val distance = kotlin.math.abs(leftMatch + trigger.length - cursor)
                    if (
                        bestTrigger == null ||
                            distance < bestDistance ||
                            (distance == bestDistance && trigger.length > bestTrigger.length)
                    ) {
                        bestRule = rule
                        bestTrigger = trigger
                        bestStart = leftMatch
                        bestDistance = distance
                    }
                }

                val rightMatch = text.indexOf(
                    trigger,
                    targetStart.coerceAtLeast(lineStart),
                    ignoreCase = false,
                ).takeIf { it in lineStart..maxStart && it != leftMatch }
                if (rightMatch != null) {
                    val distance = kotlin.math.abs(rightMatch + trigger.length - cursor)
                    if (
                        bestTrigger == null ||
                            distance < bestDistance ||
                            (distance == bestDistance && trigger.length > bestTrigger.length)
                    ) {
                        bestRule = rule
                        bestTrigger = trigger
                        bestStart = rightMatch
                        bestDistance = distance
                    }
                }
            }
        }

        return bestRule?.replacement(bestStart, bestStart + bestTrigger!!.length, cursor, rotateState)
    }

    private fun findNewLineReplacement(
        text: CharSequence,
        cursor: Int,
        rules: List<MappingRule>,
        rotateState: MutableMap<String, Int>,
    ): Replacement? {
        if (cursor <= 0 || cursor > text.length || text[cursor - 1] != '\n') return null
        val start = if (cursor >= 2 && text[cursor - 2] == '\r') cursor - 2 else cursor - 1
        val rule = rules.firstOrNull { it.isUsable(TriggerType.NEW_LINE) } ?: return null
        return rule.replacement(start, cursor, cursor, rotateState)
    }

    private fun findSendReplacement(
        text: CharSequence,
        cursor: Int,
        rules: List<MappingRule>,
        rotateState: MutableMap<String, Int>,
    ): Replacement? {
        if (text.isEmpty() || cursor !in 0..text.length) return null
        val rule = rules.firstOrNull { it.isUsable(TriggerType.SEND) } ?: return null
        return rule.replacement(0, text.length, text.length, rotateState)
    }

    private fun MappingRule.isUsable(expectedTriggerType: TriggerType): Boolean =
        enabled && id.isNotEmpty() && outputs.isNotEmpty() && triggerType == expectedTriggerType

    private fun MappingRule.replacement(
        triggerStart: Int,
        triggerEnd: Int,
        cursor: Int,
        rotateState: MutableMap<String, Int>,
    ): Replacement {
        val index = when (mode) {
            OutputMode.ROTATE -> {
                val current = rotateState[id] ?: 0
                rotateState[id] = current + 1
                current % outputs.size
            }
            OutputMode.RANDOM -> random.nextInt(outputs.size)
        }

        val (start, end) = when (executorType) {
            ExecutorType.REPLACE -> triggerStart to triggerEnd
            ExecutorType.INSERT_BEFORE -> triggerStart to triggerStart
            ExecutorType.INSERT_AFTER -> triggerEnd to triggerEnd
        }
        val newCursor = if (triggerEnd == cursor) {
            when (executorType) {
                ExecutorType.REPLACE -> start + outputs[index].length
                ExecutorType.INSERT_BEFORE -> start + outputs[index].length
                ExecutorType.INSERT_AFTER -> end + outputs[index].length
            }
        } else {
            val insertedBeforeCursor = end <= cursor
            when {
                !insertedBeforeCursor -> cursor
                executorType == ExecutorType.REPLACE -> cursor + outputs[index].length - (triggerEnd - triggerStart)
                else -> cursor + outputs[index].length
            }
        }
        return Replacement(id, outputs[index], start, end, newCursor)
    }

}