package org.dataland.datalandspecificationservice.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** A plain conversation turn. Clients cannot supply system messages or tool results. */
data class FrameworkChatMessage(
    val role: FrameworkChatRole,
    @field:NotBlank
    @field:Size(max = 6000)
    val content: String,
)

/** Allowed roles in client-supplied conversation history. */
enum class FrameworkChatRole { USER, ASSISTANT }
