package org.dataland.datalandspecificationservice.model

/** Server-recorded tool execution metadata; excludes model reasoning and full tool output. */
data class FrameworkToolCall(
    val name: String,
    val arguments: Map<String, String>,
    val successful: Boolean,
    val durationMs: Long,
)
