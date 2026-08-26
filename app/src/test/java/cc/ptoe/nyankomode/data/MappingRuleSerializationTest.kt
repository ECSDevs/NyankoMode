package cc.ptoe.nyankomode.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MappingRuleSerializationTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `single rule round trip`() {
        val rule = MappingRule(
            id = "rule-1",
            name = "自称词",
            triggers = listOf("我", "偶"),
            outputs = listOf("本喵", "喵呜"),
            mode = OutputMode.ROTATE,
            enabled = true
        )

        val encoded = json.encodeToString(rule)
        val decoded = json.decodeFromString<MappingRule>(encoded)

        assertEquals(rule, decoded)
    }

    @Test
    fun `rule list round trip`() {
        val rules = listOf(
            MappingRule(id = "a", name = "规则A", triggers = listOf("我"), outputs = listOf("本喵"), mode = OutputMode.ROTATE),
            MappingRule(id = "b", name = "规则B", triggers = listOf("你"), outputs = listOf("乃们"), mode = OutputMode.RANDOM, enabled = false)
        )

        val encoded = json.encodeToString(rules)
        val decoded = json.decodeFromString<List<MappingRule>>(encoded)

        assertEquals(rules, decoded)
    }

    @Test
    fun `missing fields decode to defaults`() {
        val encoded = """{"id":"only-id","name":"缺字段"}"""

        val decoded = json.decodeFromString<MappingRule>(encoded)

        assertEquals("only-id", decoded.id)
        assertEquals("缺字段", decoded.name)
        assertEquals(emptyList<String>(), decoded.triggers)
        assertEquals(emptyList<String>(), decoded.outputs)
        assertEquals(OutputMode.ROTATE, decoded.mode)
        assertEquals(true, decoded.enabled)
    }
}