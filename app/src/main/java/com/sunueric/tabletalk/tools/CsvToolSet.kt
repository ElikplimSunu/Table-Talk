package com.sunueric.tabletalk.tools

import android.content.Context
import android.net.Uri
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ToolSet for reading and analyzing CSV files.
 * Provides tools that the AI agent can use to access CSV data.
 */
@LLMDescription("Tools for reading and analyzing CSV files")
class CsvToolSet(private val context: Context) : ToolSet {

    /**
     * Reads a CSV file and returns the headers and first 10 rows as a preview.
     * This helps the agent understand the structure and content of the data.
     */
    @Tool(customName = "read_csv_file")
    @LLMDescription("Reads a CSV file and returns the headers and first 10 rows as a preview")
    fun readCsv(
        @LLMDescription("The content URI string of the file to read")
        uriString: String
    ): String {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return "Error: Could not open file stream."

            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            reader.close()

            if (lines.isEmpty()) return "Error: File is empty."

            val headers = lines[0]
            // Preview top 10 rows to save context window
            val preview = lines.drop(1).take(10).joinToString("\n")

            """
            CSV SCHEMA & PREVIEW:
            Headers: $headers
            
            Data (first 10 rows):
            $preview
            
            Total rows in file: ${lines.size - 1}
            """.trimIndent()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }
}
