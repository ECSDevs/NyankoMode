package cc.ptoe.nyankomode.data

import kotlinx.serialization.Serializable

@Serializable
enum class OutputMode {
    ROTATE,
    RANDOM
}

@Serializable
enum class TriggerType {
    KEYWORD,
    NEW_LINE,
    SEND,
}

@Serializable
enum class ExecutorType {
    REPLACE,
    INSERT_BEFORE,
    INSERT_AFTER,
}

@Serializable
data class MappingRule(
    val id: String = "",
    val name: String = "",
    val triggers: List<String> = emptyList(),
    val triggerType: TriggerType = TriggerType.KEYWORD,
    val executorType: ExecutorType = ExecutorType.REPLACE,
    val outputs: List<String> = emptyList(),
    val mode: OutputMode = OutputMode.ROTATE,
    val enabled: Boolean = true,
)