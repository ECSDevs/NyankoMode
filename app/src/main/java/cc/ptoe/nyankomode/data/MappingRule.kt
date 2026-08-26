package cc.ptoe.nyankomode.data

import kotlinx.serialization.Serializable

@Serializable
enum class OutputMode {
    ROTATE,
    RANDOM
}

@Serializable
data class MappingRule(
    val id: String = "",
    val name: String = "",
    val triggers: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
    val mode: OutputMode = OutputMode.ROTATE,
    val enabled: Boolean = true
)