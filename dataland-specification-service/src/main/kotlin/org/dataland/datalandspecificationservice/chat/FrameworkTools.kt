package org.dataland.datalandspecificationservice.chat

import org.dataland.datalandspecification.database.SpecificationDatabase
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam

/** Read-only framework tools shared by MCP and the frontend chat. */
class FrameworkTools(
    private val database: SpecificationDatabase,
    private val sources: MutableSet<String>? = null,
) {
    private var calls = 0

    private fun record(vararg paths: String) {
        if (sources != null) {
            check(++calls <= MAX_TOOL_CALLS) { "Tool call limit reached. Answer using the information already retrieved." }
            sources.addAll(paths)
        }
    }

    /** Lists only frameworks present in the specification catalog. */
    @Tool(
        name = "list_frameworks",
        description =
            "List framework IDs, names and descriptions in the specification catalog. " +
                "Use exact IDs to distinguish versions. This catalog may not cover every framework in the frontend.",
    )
    fun listFrameworks(): List<Map<String, String>> {
        record("/specifications/frameworks")
        return database.frameworks.values.sortedBy { it.id }.map {
            mapOf("id" to it.id, "name" to it.name, "description" to it.businessDefinition, "source" to frameworkPath(it.id))
        }
    }

    /** Searches field names, IDs, paths and English definitions without loading whole schemas into the model. */
    @Tool(
        name = "search_fields",
        description =
            "Find fields by English keywords in their name, ID, path or definition. Translate search terms to English. " +
                "Optionally restrict to an exact framework ID from list_frameworks. Returns at most 15 matches; " +
                "narrow the query if truncated. Use get_field_definition for full details.",
    )
    fun searchFields(
        @ToolParam(description = "One or more English search keywords") query: String,
        @ToolParam(description = "Exact framework ID, or omit to search all frameworks", required = false) frameworkId: String?,
    ): Map<String, Any> {
        require(query.isNotBlank() && query.length <= MAX_QUERY_LENGTH) { "Provide between 1 and 200 search characters." }
        require(frameworkId == null || database.frameworks.containsKey(frameworkId)) { "Unknown framework ID. Use list_frameworks." }
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val matches =
            database.frameworks.values
                .asSequence()
                .filter { frameworkId == null || it.id == frameworkId }
                .sortedBy { it.id }
                .flatMap { framework ->
                    framework.flattenedSchema.asSequence().mapNotNull { entry ->
                        val field = database.dataPointTypes[entry.value] ?: return@mapNotNull null
                        val text = "${field.id} ${field.name} ${field.businessDefinition} ${entry.jsonPath}".lowercase()
                        if (terms.all { it in text }) {
                            mapOf(
                                "frameworkId" to framework.id, "path" to entry.jsonPath, "id" to field.id,
                                "name" to field.name, "description" to field.businessDefinition.take(SNIPPET_LENGTH),
                                "source" to fieldPath(field.id),
                            )
                        } else {
                            null
                        }
                    }
                }.take(MAX_RESULTS + 1)
                .toList()
        record(*matches.take(MAX_RESULTS).map { it.getValue("source") }.toTypedArray())
        return mapOf("matches" to matches.take(MAX_RESULTS), "truncated" to (matches.size > MAX_RESULTS))
    }

    /** Returns the authoritative definition and its declared validation and calculation metadata. */
    @Tool(
        name = "get_field_definition",
        description =
            "Get a field's full definition, base type, constraints, example, framework paths and calculation rules. " +
                "Use IDs returned by search_fields. Examples are not company data. " +
                "Empty calculation rules do not imply no business dependencies.",
    )
    fun getFieldDefinition(
        @ToolParam(description = "Exact data point type ID") dataPointTypeId: String,
    ): Map<String, Any> {
        val field = requireNotNull(database.dataPointTypes[dataPointTypeId]) { "Unknown data point ID. Use search_fields." }
        val baseType = database.dataPointBaseTypes.getValue(field.dataPointBaseTypeId)
        record(fieldPath(field.id), "/specifications/data-point-base-types/${baseType.id}")
        return mapOf(
            "id" to field.id, "name" to field.name, "description" to field.businessDefinition,
            "constraints" to field.constraints.orEmpty(), "calculationRules" to field.calculationRules,
            "baseType" to
                mapOf(
                    "id" to baseType.id, "name" to baseType.name, "description" to baseType.businessDefinition,
                    "exampleJson" to baseType.example.toString(),
                ),
            "usedBy" to
                database.frameworks.values.sortedBy { it.id }.flatMap { framework ->
                    framework.flattenedSchema.filter { it.value == field.id }.map {
                        mapOf("frameworkId" to framework.id, "path" to it.jsonPath)
                    }
                },
            "source" to fieldPath(field.id),
        )
    }

    private fun frameworkPath(id: String) = "/specifications/frameworks/$id"

    private fun fieldPath(id: String) = "/specifications/data-point-types/$id"

    companion object {
        private const val MAX_RESULTS = 15
        private const val MAX_QUERY_LENGTH = 200
        private const val SNIPPET_LENGTH = 300
        private const val MAX_TOOL_CALLS = 10
    }
}
