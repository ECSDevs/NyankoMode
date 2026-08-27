package cc.ptoe.nyankomode.engine

import cc.ptoe.nyankomode.data.ExecutorType
import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.OutputMode
import cc.ptoe.nyankomode.data.TriggerType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingEngineTest {

    private val engine = MappingEngine()

    @Test
    fun `multiple triggers hit same rule`() {
        val rule = MappingRule(id = "r1", triggers = listOf("我", "偶"), outputs = listOf("本喵"))
        val result = engine.findReplacement("你好我", 3, listOf(rule))
        assertEquals("ruleId", "r1", result?.ruleId)
        assertEquals("start", 2, result?.start)
        assertEquals("end", 3, result?.end)
        assertEquals("output", "本喵", result?.output)
        assertEquals("newCursor", 4, result?.newCursor)
    }

    @Test
    fun `trigger adjacent to cursor not at end of text`() {
        val rule = MappingRule(id = "r2", triggers = listOf("我"), outputs = listOf("本喵"))
        val result = engine.findReplacement("abc我def", 4, listOf(rule))
        assertEquals("start", 3, result?.start)
        assertEquals("end", 4, result?.end)
        assertEquals("output", "本喵", result?.output)
    }

    @Test
    fun `longest trigger wins regardless of rule order`() {
        val a = MappingRule(id = "A", triggers = listOf("天"), outputs = listOf("A天"))
        val b = MappingRule(id = "B", triggers = listOf("今天"), outputs = listOf("B今天"))

        val r1 = engine.findReplacement("a今天", 3, listOf(a, b))
        assertEquals("B", r1?.ruleId)

        val r2 = engine.findReplacement("a今天", 3, listOf(b, a))
        assertEquals("B", r2?.ruleId)
    }

    @Test
    fun `rotate cycles through outputs`() {
        val rule = MappingRule(id = "r3", triggers = listOf("喵"), outputs = listOf("本喵", "喵呜"), mode = OutputMode.ROTATE)
        val state = mutableMapOf<String, Int>()
        val o1 = engine.findReplacement("a喵", 2, listOf(rule), state)?.output
        val o2 = engine.findReplacement("a喵", 2, listOf(rule), state)?.output
        val o3 = engine.findReplacement("a喵", 2, listOf(rule), state)?.output
        assertEquals("本喵", o1)
        assertEquals("喵呜", o2)
        assertEquals("本喵", o3)
    }

    @Test
    fun `random outputs from list`() {
        val rule = MappingRule(id = "r4", triggers = listOf("喵"), outputs = listOf("本喵", "喵呜"), mode = OutputMode.RANDOM)
        val randomEngine = MappingEngine(Random(42))
        val outputs = (0..50).map {
            randomEngine.findReplacement("a喵", 2, listOf(rule))?.output
        }
        assertTrue(outputs.all { it == "本喵" || it == "喵呜" })
    }

    @Test
    fun `keyword scans the whole current line when cursor is unreliable`() {
        val rule = MappingRule(id = "line", triggers = listOf("喵"), outputs = listOf("本喵"))

        val result = engine.findReplacement("前喵后", 0, listOf(rule))

        assertEquals("line", result?.ruleId)
        assertEquals(1, result?.start)
        assertEquals(2, result?.end)
        assertEquals("本喵", result?.output)
    }

    @Test
    fun `keyword scan stays within the current line`() {
        val rule = MappingRule(id = "line", triggers = listOf("喵"), outputs = listOf("本喵"))

        assertNull(engine.findReplacement("喵\n后", 2, listOf(rule)))
    }

    @Test
    fun `keyword chooses the occurrence closest to the cursor`() {
        val rule = MappingRule(id = "nearest", triggers = listOf("喵"), outputs = listOf("本喵"))

        val result = engine.findReplacement("喵abcd喵", 4, listOf(rule))

        assertEquals(5, result?.start)
        assertEquals(6, result?.end)
    }

    @Test
    fun `equidistant keyword occurrences keep the earlier match`() {
        val rule = MappingRule(id = "earlier", triggers = listOf("喵"), outputs = listOf("本喵"))

        val result = engine.findReplacement("喵a喵", 2, listOf(rule))

        assertEquals(0, result?.start)
        assertEquals(1, result?.end)
    }

    @Test
    fun `keyword rejects an invalid cursor`() {
        val rule = MappingRule(id = "r5", triggers = listOf("喵"), outputs = listOf("喵喵"))

        assertNull(engine.findReplacement("a喵", -1, listOf(rule)))
    }

    @Test
    fun `invalid rules return null`() {
        val disabled = MappingRule(id = "r6", triggers = listOf("喵"), outputs = listOf("喵喵"), enabled = false)
        val noTriggers = MappingRule(id = "r7", triggers = emptyList(), outputs = listOf("喵喵"))
        val noOutputs = MappingRule(id = "r8", triggers = listOf("喵"), outputs = emptyList())
        val noId = MappingRule(id = "", triggers = listOf("喵"), outputs = listOf("喵喵"))

        assertNull(engine.findReplacement("a喵", 2, listOf(disabled)))
        assertNull(engine.findReplacement("a喵", 2, listOf(noTriggers)))
        assertNull(engine.findReplacement("a喵", 2, listOf(noOutputs)))
        assertNull(engine.findReplacement("a喵", 2, listOf(noId)))
    }

    @Test
    fun `typing simulation preserves a replacement after later characters`() {
        val rule = MappingRule(id = "preview", triggers = listOf("我"), outputs = listOf("本喵"))

        val result = engine.simulateTyping("我好", listOf(rule))

        assertEquals("本喵好", result)
    }

    @Test
    fun `new line replaces line break`() {
        val rule = MappingRule(
            id = "line",
            triggerType = TriggerType.NEW_LINE,
            outputs = listOf(" | "),
        )

        val result = engine.findReplacement(
            text = "前\n",
            cursor = 2,
            rules = listOf(rule),
            triggerType = TriggerType.NEW_LINE,
        )

        assertEquals("line", result?.ruleId)
        assertEquals(1, result?.start)
        assertEquals(2, result?.end)
        assertEquals(" | ", result?.output)
        assertEquals(4, result?.newCursor)
    }

    @Test
    fun `new line replaces CRLF as one trigger`() {
        val rule = MappingRule(
            id = "line-crlf",
            triggerType = TriggerType.NEW_LINE,
            outputs = listOf("<br>"),
        )

        val result = engine.findReplacement(
            text = "前\r\n",
            cursor = 3,
            rules = listOf(rule),
            triggerType = TriggerType.NEW_LINE,
        )

        assertEquals(1, result?.start)
        assertEquals(3, result?.end)
        assertEquals("<br>", result?.output)
    }

    @Test
    fun `send replaces the whole non-empty input`() {
        val rule = MappingRule(
            id = "send",
            triggerType = TriggerType.SEND,
            outputs = listOf("已发送"),
        )

        val result = engine.findReplacement(
            text = "待发送内容",
            cursor = 2,
            rules = listOf(rule),
            triggerType = TriggerType.SEND,
        )

        assertEquals("send", result?.ruleId)
        assertEquals(0, result?.start)
        assertEquals(5, result?.end)
        assertEquals("已发送", result?.output)
        assertEquals(3, result?.newCursor)
    }

    @Test
    fun `trigger type isolates rules`() {
        val keyword = MappingRule(id = "keyword", triggers = listOf("喵"), outputs = listOf("喵喵"))
        val send = MappingRule(id = "send", triggerType = TriggerType.SEND, outputs = listOf("发送"))

        assertNull(engine.findReplacement("a喵", 2, listOf(send)))
        assertNull(
            engine.findReplacement(
                text = "a喵",
                cursor = 2,
                rules = listOf(keyword),
                triggerType = TriggerType.SEND,
            ),
        )
    }

    @Test
    fun `executor type controls replacement bounds`() {
        val replace = MappingRule(
            id = "replace",
            triggers = listOf("喵"),
            outputs = listOf("本喵"),
            executorType = ExecutorType.REPLACE,
        )
        val insertBefore = replace.copy(
            id = "before",
            executorType = ExecutorType.INSERT_BEFORE,
        )
        val insertAfter = replace.copy(
            id = "after",
            executorType = ExecutorType.INSERT_AFTER,
        )

        val replaced = engine.findReplacement("a喵b", 2, listOf(replace))
        val before = engine.findReplacement("a喵b", 2, listOf(insertBefore))
        val after = engine.findReplacement("a喵b", 2, listOf(insertAfter))

        assertEquals(1, replaced?.start)
        assertEquals(2, replaced?.end)
        assertEquals(3, replaced?.newCursor)
        assertEquals("a本喵b", "a喵b".replaceRange(replaced!!.start, replaced.end, replaced.output))
        assertEquals(1, before?.start)
        assertEquals(1, before?.end)
        assertEquals(3, before?.newCursor)
        assertEquals("a本喵喵b", "a喵b".replaceRange(before!!.start, before.end, before.output))
        assertEquals(2, after?.start)
        assertEquals(2, after?.end)
        assertEquals(4, after?.newCursor)
        assertEquals("a喵本喵b", "a喵b".replaceRange(after!!.start, after.end, after.output))
    }
    @Test
    fun `send on empty input returns null`() {
        val rule = MappingRule(id = "send", triggerType = TriggerType.SEND, outputs = listOf("发送"))

        assertNull(
            engine.findReplacement(
                text = "",
                cursor = 0,
                rules = listOf(rule),
                triggerType = TriggerType.SEND,
            ),
        )
    }
}