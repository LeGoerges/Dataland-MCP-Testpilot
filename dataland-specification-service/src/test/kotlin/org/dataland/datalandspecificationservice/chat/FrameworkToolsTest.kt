package org.dataland.datalandspecificationservice.chat

import org.dataland.datalandbackendutils.utils.JsonUtils.defaultObjectMapper
import org.dataland.datalandspecification.database.SpecificationDatabase
import org.dataland.datalandspecification.specifications.DataPointBaseType
import org.dataland.datalandspecification.specifications.DataPointType
import org.dataland.datalandspecification.specifications.Framework
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Tests field discovery, exact version selection and request-local source collection. */
class FrameworkToolsTest {
    private val database = object : SpecificationDatabase() {}

    init {
        database.dataPointBaseTypes["decimal"] =
            DataPointBaseType(
                "decimal", "Decimal", "A decimal number", "validator",
                defaultObjectMapper.readTree("{}"), defaultObjectMapper.readTree("12.5"),
            )
        database.dataPointTypes["emissions"] =
            DataPointType(
                "emissions", "Scope 1 emissions", "Direct greenhouse gas emissions in tonnes", "decimal", emptySet(), listOf("min:0"),
            )
        for (id in listOf("sfdr", "sfdr-v2")) {
            database.frameworks[id] =
                Framework(id, id, "Framework $id").apply {
                    schema.putObject("environmental").put("scope1", "emissions")
                }
        }
    }

    @Test
    fun `search resolves paths and honors exact framework versions`() {
        val tools = FrameworkTools(database)
        val result = tools.searchFields("greenhouse tonnes", "sfdr-v2")
        val matches = result.getValue("matches") as List<*>
        assertEquals(1, matches.size)
        val match = matches.single() as Map<*, *>
        assertEquals("sfdr-v2", match["frameworkId"])
        assertEquals(".environmental.scope1", match["path"])
        assertEquals("emissions", match["id"])
        assertFalse(result.getValue("truncated") as Boolean)
        assertThrows<IllegalArgumentException> { tools.searchFields("emissions", "unknown") }
        assertThrows<IllegalArgumentException> { tools.searchFields(" ", null) }
    }

    @Test
    fun `search is bounded and signals truncation`() {
        repeat(20) {
            database.frameworks
                .getValue("sfdr")
                .schema
                .put("field$it", "emissions")
        }
        val result = FrameworkTools(database).searchFields("emissions", null)
        assertEquals(15, (result.getValue("matches") as List<*>).size)
        assertTrue(result.getValue("truncated") as Boolean)
    }

    @Test
    fun `definition returns metadata and only retrieved sources are collected per request`() {
        val sources = linkedSetOf<String>()
        val tools = FrameworkTools(database, sources)
        val result = tools.getFieldDefinition("emissions")
        assertEquals(listOf("min:0"), result["constraints"])
        assertEquals(2, (result["usedBy"] as List<*>).size)
        assertEquals(
            setOf("/specifications/data-point-types/emissions", "/specifications/data-point-base-types/decimal"), sources,
        )
        val otherSources = linkedSetOf<String>()
        FrameworkTools(database, otherSources).listFrameworks()
        assertEquals(setOf("/specifications/frameworks"), otherSources)
        assertThrows<IllegalArgumentException> { tools.getFieldDefinition("missing") }
    }
}
