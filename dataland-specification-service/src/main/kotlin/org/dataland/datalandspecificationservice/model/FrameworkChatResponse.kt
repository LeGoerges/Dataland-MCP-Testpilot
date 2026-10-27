package org.dataland.datalandspecificationservice.model

/** Answer and specification paths actually retrieved by the tools during this request. */
data class FrameworkChatResponse(
    val answer: String,
    val sources: List<String>,
)
