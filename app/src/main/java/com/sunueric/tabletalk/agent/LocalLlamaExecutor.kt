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

        val llamaPromptString = buildLlamaPrompt(prompt.messages)
        val responseText = LlamaBridge.generate(llamaPromptString)

        // Return the Assistant message with empty metadata (local model doesn't track tokens)
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
        val llamaPromptString = buildLlamaPrompt(prompt.messages)
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
    private fun buildLlamaPrompt(messages: List<Message>): String {
        val sb = StringBuilder()
        messages.forEach { msg ->
            when (msg) {
                is Message.System -> sb.append("<|start_header_id|>system<|end_header_id|>\n\n${msg.content}<|eot_id|>")
                is Message.User -> sb.append("<|start_header_id|>user<|end_header_id|>\n\n${msg.content}<|eot_id|>")
                is Message.Assistant -> sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n${msg.content}<|eot_id|>")
                else -> {}
            }
        }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }
}