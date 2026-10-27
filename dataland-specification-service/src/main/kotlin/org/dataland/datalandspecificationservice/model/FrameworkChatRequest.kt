package org.dataland.datalandspecificationservice.model

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** A question and a bounded, transient history kept by the browser. */
data class FrameworkChatRequest(
    @field:NotBlank
    @field:Size(max = 2000)
    val message: String,
    @field:Size(max = 10)
    @field:Valid
    val history: List<FrameworkChatMessage> = emptyList(),
)
