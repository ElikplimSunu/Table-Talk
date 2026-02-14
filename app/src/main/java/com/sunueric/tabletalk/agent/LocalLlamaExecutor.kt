package com.sunueric.tabletalk.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LocalLlamaExecutor : PromptExecutor {

    // 1. EXECUTE
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {

        // Inject tool instructions if tools are present
        val toolInstructions = if (tools.isNotEmpty()) buildToolInstructions(tools) else ""
        
        val llamaPromptString = buildLlamaPrompt(prompt.messages, toolInstructions)
        val responseText = LlamaBridge.generate(llamaPromptString)

        // Return the raw text. The Agent's strategy (extractToolCalls) will parse this.
        return listOf(
            Message.Assistant(
                content = responseText,
                metaInfo = ResponseMetaInfo.Empty
            )
        )
    }

    // 2. STREAMING
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        // Get full response (non-streaming since LlamaBridge.generate is blocking)
        val toolInstructions = if (tools.isNotEmpty()) buildToolInstructions(tools) else ""
        val llamaPromptString = buildLlamaPrompt(prompt.messages, toolInstructions)
        val responseText = LlamaBridge.generate(llamaPromptString)

        // Emit the full text as an Append frame
        emit(StreamFrame.Append(text = responseText))

        // Signal end of stream with finish reason and metadata
        emit(StreamFrame.End(finishReason = "stop", metaInfo = ResponseMetaInfo.Empty))
    }

    // 3. MODERATION
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        // Local model - no moderation needed, return non-harmful result
        return ModerationResult(isHarmful = false, categories = emptyMap())
    }

    override fun close() {}

    // --- Helper ---
    // Uses TableLLM's Text Answer format with [INST]...[INST/] tags
    // Reference: https://huggingface.co/RUCKBReasoning/TableLLM-7b#text-answer
    private fun buildLlamaPrompt(messages: List<Message>, toolInstructions: String = ""): String {
        val sb = StringBuilder()
        sb.append("[INST]")
        
        // Add tool instructions first if present
        if (toolInstructions.isNotEmpty()) {
            sb.append("SYSTEM:\n$toolInstructions\n\n")
        }
        
        messages.forEach { msg ->
            when (msg) {
                is Message.System -> sb.append("${msg.content}\n\n")
                is Message.User -> sb.append("${msg.content}\n\n")
                is Message.Assistant -> sb.append("[INST/]${msg.content}[INST]")
                else -> {}
            }
        }
        
        sb.append("[INST/]")
        return sb.toString()
    }

    private fun buildToolInstructions(tools: List<ToolDescriptor>): String {
        val toolsJson = tools.joinToString(",\n") { tool ->
            """
            {
                "name": "${tool.name}",
                "description": "${tool.description}",
                "parameters": "See args"
            }
            """.trimIndent()
        }
        
        return """
            You have access to the following tools:
            [
            $toolsJson
            ]
            
            To use a tool, output ONLY a JSON object in this format:
            { "tool": "tool_name", "args": { "arg_name": "value" } }
            
            If you do not need to use a tool, just answer normally.
        """.trimIndent()
    }
}