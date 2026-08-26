package cc.ptoe.nyankomode.engine

import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.OutputMode
import kotlin.random.Random

/** 一次命中的替换结果 */
data class Replacement(
    val ruleId: String,
    val output: String,
    val start: Int,
    val end: Int,
) {
    /** 替换后应将光标移动到的位置 */
    val newCursor: Int get() = start + output.length
}

class MappingEngine(private val random: Random = Random.Default) {

    fun findReplacement(
        text: String,
        cursor: Int,
        rules: List<MappingRule>,
        rotateState: MutableMap<String, Int> = mutableMapOf(),
    ): Replacement? {
        if (cursor <= 0) return null

        var bestRule: MappingRule? = null
        var bestTrigger: String? = null
        var bestStart = -1

        for (rule in rules) {
            if (!rule.enabled) continue
            if (rule.id.isNotEmpty() &&
                rule.triggers.isNotEmpty() &&
                rule.outputs.isNotEmpty()
            ) {
                for (t in rule.triggers) {
                    if (t.isEmpty()) continue
                    val start = cursor - t.length
                    if (start >= 0 && text.regionMatches(start, t, 0, t.length)) {
                        if (bestTrigger == null || t.length > bestTrigger.length) {
                            bestRule = rule
                            bestTrigger = t
                            bestStart = start
                        }
                    }
                }
            }
        }

        val rule = bestRule ?: return null
        val outputs = rule.outputs
        if (outputs.isEmpty()) return null

        val idx = when (rule.mode) {
            OutputMode.ROTATE -> {
                val current = rotateState[rule.id] ?: 0
                rotateState[rule.id] = current + 1
                current % outputs.size
            }
            OutputMode.RANDOM -> random.nextInt(outputs.size)
        }

        return Replacement(rule.id, outputs[idx], bestStart, cursor)
    }
}