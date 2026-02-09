package com.sunueric.tabletalk.agent

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Custom LLModel for local Llama model running via LlamaBridge.
 * Uses Meta as the provider since Llama is a Meta model.
 */
val LocalLlamaModel = LLModel(
    provider = LLMProvider.Meta,
    id = "llama-local-gguf",
    capabilities = listOf(LLMCapability.Completion),
    contextLength = 4096,
    maxOutputTokens = 1024
)
