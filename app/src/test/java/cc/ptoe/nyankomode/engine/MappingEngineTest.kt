package cc.ptoe.nyankomode.engine

import cc.ptoe.nyankomode.data.MappingRule
import cc.ptoe.nyankomode.data.OutputMode
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
    fun `cursor zero returns null`() {
        val rule = MappingRule(id = "r5", triggers = listOf("喵"), outputs = listOf("喵喵"))
        assertNull(engine.findReplacement("a喵", 0, listOf(rule)))
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
}