package com.sunueric.tabletalk.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/**
 * Tool for analyzing CSV data. Uses SimpleTool pattern from Koog.
 * This tool receives CSV context and a query, returns analysis.
 */

@LLMDescription("CSV Analysis Tool")
class AnalyzeCsvTool(
    private val csvData: String
)
    : SimpleTool<AnalyzeCsvTool.Args>(
    argsSerializer = Args.serializer(),
    name = "analyze_csv",
    description = "Analyze CSV data and answer questions about it"
) {
    @Serializable
    data class Args(
        val query: String
    )

    override suspend fun execute(args: Args): String {
        // In hybrid mode, we inject context directly
        // This tool returns the CSV context for the agent to use
        return """
            CSV DATA CONTEXT:
            $csvData
            
            User Query: ${args.query}
            
            Please analyze the data above and answer the query.
        """.trimIndent()
    }
}

/**
 * Tool for getting CSV schema (headers).
 */
class GetCsvSchemaTool(
    private val headers: String
) : SimpleTool<GetCsvSchemaTool.EmptyArgs>(
    argsSerializer = EmptyArgs.serializer(),
    name = "get_csv_schema",
    description = "Get the column headers/schema of the loaded CSV file"
) {
    override suspend fun execute(args: EmptyArgs): String {
        return "CSV Schema (Headers): $headers"
    }

    @Serializable
    object EmptyArgs
}

/**
 * Tool for searching CSV data.
 */
class SearchCsvTool(
    private val csvLines: List<String>
) : SimpleTool<SearchCsvTool.Args>(
    argsSerializer = Args.serializer(),
    name = "search_csv",
    description = "Search for rows containing a specific value"
) {
    @Serializable
    data class Args(
        val searchTerm: String,
        val maxResults: Int = 10
    )

    override suspend fun execute(args: Args): String {
        val matches = csvLines
            .filter { it.contains(args.searchTerm, ignoreCase = true) }
            .take(args.maxResults)
        
        return if (matches.isEmpty()) {
            "No rows found containing '${args.searchTerm}'"
        } else {
            "Found ${matches.size} rows:\n${matches.joinToString("\n")}"
        }
    }
}
