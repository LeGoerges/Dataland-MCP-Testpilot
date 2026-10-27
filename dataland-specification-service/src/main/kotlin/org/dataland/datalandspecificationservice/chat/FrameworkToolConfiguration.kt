package org.dataland.datalandspecificationservice.chat

import org.dataland.datalandspecification.database.SpecificationDatabase
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Exposes the same read-only tools over MCP without requiring a model or credentials. */
@Configuration
class FrameworkToolConfiguration {
    /** Registers framework functions for automatic MCP tool discovery. */
    @Bean
    fun frameworkToolCallbacks(database: SpecificationDatabase): ToolCallbackProvider =
        MethodToolCallbackProvider.builder().toolObjects(FrameworkTools(database)).build()
}
