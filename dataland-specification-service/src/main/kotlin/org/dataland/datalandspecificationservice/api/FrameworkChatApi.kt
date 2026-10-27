package org.dataland.datalandspecificationservice.api

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.dataland.datalandspecificationservice.model.FrameworkChatRequest
import org.dataland.datalandspecificationservice.model.FrameworkChatResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/** Frontend API for questions grounded in the framework specification catalog. */
interface FrameworkChatApi {
    /** Answers a question using the specification tools and optional conversation history. */
    @Operation(summary = "Ask a question about Dataland framework specifications")
    @PostMapping("/chat", consumes = ["application/json"], produces = ["application/json"])
    fun chat(
        @Valid @RequestBody request: FrameworkChatRequest,
    ): FrameworkChatResponse
}
