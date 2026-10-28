package org.dataland.datalandspecificationservice.chat

import org.dataland.datalandbackendutils.utils.JsonUtils.defaultObjectMapper
import org.dataland.datalandspecification.database.SpecificationDatabase
import org.dataland.datalandspecification.specifications.DataPointBaseType
import org.dataland.datalandspecification.specifications.DataPointType
import org.dataland.datalandspecification.specifications.Framework
import org.dataland.datalandspecificationservice.model.FrameworkToolCall
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

    @Test
    fun `comparison counts IDs once per framework and distinguishes pairwise overlap from all frameworks`() {
        database.frameworks
            .getValue("sfdr")
            .schema
            .put("anotherPath", "emissions")
        database.dataPointTypes["different-id"] = database.dataPointTypes.getValue("emissions").copy(id = "different-id")
        database.frameworks["pcaf"] = Framework("pcaf", "PCAF", "Test").apply { schema.put("scope1", "different-id") }
        val tools = FrameworkTools(database)
        val result = tools.compareFrameworks(null, null)
        assertEquals(mapOf("pcaf" to 1, "sfdr" to 1, "sfdr-v2" to 1), result["fieldCounts"])
        assertEquals(0, result["sharedByAllCount"])
        assertEquals(1, result["totalSharedFields"])
        assertEquals(
            listOf(mapOf("id" to "emissions", "name" to "Scope 1 emissions", "frameworkIds" to listOf("sfdr", "sfdr-v2"))),
            result["sharedFields"],
        )
        val pairs = result["pairs"] as List<*>
        assertEquals(mapOf("frameworkIds" to listOf("sfdr", "sfdr-v2"), "sharedCount" to 1), pairs.last())
        assertEquals(1, tools.compareFrameworks(listOf("sfdr", "sfdr-v2"), null)["sharedByAllCount"])
        assertThrows<IllegalArgumentException> { tools.compareFrameworks(listOf("missing", "sfdr"), null) }
        assertThrows<IllegalArgumentException> { tools.compareFrameworks(listOf("sfdr", "sfdr"), null) }
        assertThrows<IllegalArgumentException> { tools.compareFrameworks(null, -1) }
    }

    @Test
    fun `comparison pages shared fields while keeping exact counts`() {
        repeat(20) { index ->
            val id = "field-$index"
            database.dataPointTypes[id] = database.dataPointTypes.getValue("emissions").copy(id = id)
            database.frameworks.values.forEach { it.schema.put(id, id) }
        }
        val tools = FrameworkTools(database)
        val first = tools.compareFrameworks(null, 0)
        val second = tools.compareFrameworks(null, 15)
        assertEquals(21, first["totalSharedFields"])
        assertEquals(21, second["totalSharedFields"])
        assertTrue(first["hasMore"] as Boolean)
        assertFalse(second["hasMore"] as Boolean)
        assertEquals(15, (first["sharedFields"] as List<*>).size)
        assertEquals(6, (second["sharedFields"] as List<*>).size)
    }

    @Test
    fun `tool trace records execution arguments status and request isolation`() {
        val calls = mutableListOf<FrameworkToolCall>()
        val sources = linkedSetOf<String>()
        val tools = FrameworkTools(database, sources, calls)
        tools.listFrameworks()
        tools.compareFrameworks(listOf("sfdr", "sfdr-v2"), 0)
        assertThrows<IllegalArgumentException> { tools.getFieldDefinition("unknown") }
        assertEquals(listOf("list_frameworks", "compare_frameworks", "get_field_definition"), calls.map { it.name })
        assertEquals(mapOf("frameworkIds" to "sfdr, sfdr-v2", "offset" to "0"), calls[1].arguments)
        assertTrue(calls[0].successful)
        assertFalse(calls[2].successful)
        assertTrue(calls.all { it.durationMs >= 0 })
        assertTrue("/specifications/frameworks/sfdr-v2" in sources)
        FrameworkTools(database).listFrameworks()
        assertEquals(3, calls.size)
    }
}
