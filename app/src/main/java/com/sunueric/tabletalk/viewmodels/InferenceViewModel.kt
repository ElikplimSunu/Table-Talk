package com.sunueric.tabletalk.viewmodels

import ai.koog.agents.core.tools.ToolRegistry
import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.llamatik.library.platform.LlamaBridge
import com.sunueric.tabletalk.agent.AnalyzeCsvTool
import com.sunueric.tabletalk.agent.GetCsvSchemaTool
import com.sunueric.tabletalk.agent.LocalLlamaModel
import com.sunueric.tabletalk.agent.SearchCsvTool
import com.sunueric.tabletalk.data.ChatMessage
import com.sunueric.tabletalk.ui.states.InferenceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ViewModel for managing AI inference with Koog AIAgent and local Llama model.
 * Uses hybrid approach: Koog agent structure with manual context injection.
 */
class InferenceViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "InferenceViewModel"
    }

    // Maintained for backward compatibility / initial status, but ChatHistory is main driver now
    private val _uiState = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory = _chatHistory.asStateFlow()

    // Exposed state for UI
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded = _isModelLoaded.asStateFlow()
    
    private val _isCsvLoaded = MutableStateFlow(false)
    val isCsvLoaded = _isCsvLoaded.asStateFlow()

    private var currentModelPath: String? = null
    private var currentCsvData: String? = null
    private var currentCsvHeaders: String? = null
    private var currentCsvLines: List<String> = emptyList()

    // Koog ToolRegistry - built dynamically with CSV context
    private var toolRegistry: ToolRegistry? = null

    /**
     * Initialize the local Llama model.
     */
    fun initializeAndTest(modelPath: String) {
        Log.d(TAG, "initializeAndTest called with path: $modelPath")
        Log.d(TAG, "Current model state - isLoaded: ${_isModelLoaded.value}, currentPath: $currentModelPath")

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = InferenceState.Thinking("Loading AI Model...")
            Log.d(TAG, "Starting model initialization...")

            try {
                // Check if file exists and is readable
                val modelFile = java.io.File(modelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file does not exist at path: $modelPath")
                    _uiState.value = InferenceState.Error("Model file not found at: $modelPath")
                    return@launch
                }
                
                if (!modelFile.canRead()) {
                    Log.e(TAG, "Cannot read model file - permission denied")
                    _uiState.value = InferenceState.Error("Cannot read model file. Check storage permissions.")
                    return@launch
                }
                
                if (modelFile.length() == 0L) {
                    Log.e(TAG, "Model file is empty")
                    _uiState.value = InferenceState.Error("Model file is empty or corrupted.")
                    return@launch
                }

                // If a different model was loaded before, shutdown first
                if (_isModelLoaded.value && currentModelPath != modelPath) {
                    Log.d(TAG, "Different model path, shutting down previous model...")
                    try {
                        LlamaBridge.shutdown()
                    } catch (e: Exception) {
                        Log.w(TAG, "Shutdown failed (may not have been loaded): ${e.message}")
                    }
                    _isModelLoaded.value = false
                }

                // Try to load the model
                Log.d(TAG, "Calling LlamaBridge.initGenerateModel with verified file...")
                _uiState.value = InferenceState.Thinking("Loading ${modelFile.length() / 1024 / 1024} MB model...")
                
                val loadSuccess = LlamaBridge.initGenerateModel(modelPath)
                Log.d(TAG, "initGenerateModel returned: $loadSuccess")
                
                if (loadSuccess) {
                    _isModelLoaded.value = true
                    currentModelPath = modelPath
                    Log.i(TAG, "Model loaded successfully!")
                    _uiState.value = InferenceState.Success("Model Ready! Upload a CSV file to start analyzing.")
                    
                    // Add system message to chat
                    addMessage(ChatMessage.AgentMessage("Model loaded successfully! Please upload a CSV file to begin analysis."))
                } else {
                    val errorMsg = "Model failed to load. Possible causes:\n" +
                            "• Unsupported GGUF format\n" +
                            "• Corrupted model file\n" +
                            "• Insufficient device memory"
                    
                    Log.e(TAG, "initGenerateModel returned false")
                    _isModelLoaded.value = false
                    currentModelPath = null
                    _uiState.value = InferenceState.Error(errorMsg)
                    addMessage(ChatMessage.ErrorMessage(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model initialization failed", e)
                _isModelLoaded.value = false
                currentModelPath = null
                _uiState.value = InferenceState.Error("Init Failed: ${e.message}")
                addMessage(ChatMessage.ErrorMessage("Init Failed: ${e.message}"))
            }
        }
    }


    /**
     * Load a CSV file and build Koog tools with the data context.
     */
    fun loadCsvFile(uriString: String) {
        Log.d(TAG, "loadCsvFile called with URI: $uriString")

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = InferenceState.Thinking("Loading CSV file...")
            addMessage(ChatMessage.ThinkingMessage)
            
            try {
                val uri = uriString.toUri()
                val context = getApplication<Application>()
                
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Could not open file")

                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines()
                reader.close()

                if (lines.isEmpty()) {
                    removeThinkingMessage()
                    Log.w(TAG, "CSV file is empty")
                    _uiState.value = InferenceState.Error("Error: File is empty.")
                    addMessage(ChatMessage.ErrorMessage("Error: File is empty."))
                    return@launch
                }

                // Store CSV data
                currentCsvHeaders = lines[0]
                currentCsvLines = lines.drop(1)
                
                // Parse headers
                val headers = currentCsvHeaders?.split(",") ?: emptyList()
                
                // Parse preview rows (limit to 10 for preview, but keep full data for tools)
                val previewRows = currentCsvLines.take(10).map { line ->
                    line.split(",")
                }

                // Format as raw CSV for LLM
                val dataRows = currentCsvLines.take(10)
                val rawCsvPreview = listOf(currentCsvHeaders).plus(dataRows).joinToString("\n")
                currentCsvData = rawCsvPreview

                // Build Koog ToolRegistry with CSV context
                buildToolRegistry()
                _isCsvLoaded.value = true
                Log.i(TAG, "CSV loaded and ToolRegistry built")

                _uiState.value = InferenceState.Success("CSV Loaded!")
                
                // Remove thinking and add Table Message
                removeThinkingMessage()
                addMessage(ChatMessage.TableMessage(
                    headers = headers,
                    rows = previewRows,
                    totalRows = currentCsvLines.size
                ))
                addMessage(ChatMessage.AgentMessage("I've loaded your CSV file. You can now ask questions about this data."))

            } catch (e: Exception) {
                removeThinkingMessage()
                Log.e(TAG, "Error loading CSV", e)
                _uiState.value = InferenceState.Error("Error loading CSV: ${e.message}")
                addMessage(ChatMessage.ErrorMessage("Error loading CSV: ${e.message}"))
            }
        }
    }

    /**
     * Build Koog ToolRegistry with CSV analysis tools.
     */
    private fun buildToolRegistry() {
        toolRegistry = ToolRegistry {
            tool(AnalyzeCsvTool(currentCsvData ?: ""))
            tool(GetCsvSchemaTool(currentCsvHeaders ?: ""))
            tool(SearchCsvTool(currentCsvLines))
        }
    }

    /**
     * Ask a question using Koog AIAgent (hybrid mode with context injection).
     */
    fun askQuestion(userQuery: String) {
        if (!_isModelLoaded.value) {
            addMessage(ChatMessage.ErrorMessage("Model not loaded yet. Please load a model first."))
            return
        }

        // Add user message
        addMessage(ChatMessage.UserMessage(userQuery))
        addMessage(ChatMessage.ThinkingMessage)
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = runAgentWithContext(userQuery)
                
                removeThinkingMessage()
                
                if (response.isBlank()) {
                    addMessage(ChatMessage.ErrorMessage("Model returned empty response. Try reloading the model."))
                } else {
                    addMessage(ChatMessage.AgentMessage(response))
                }
            } catch (e: Exception) {
                removeThinkingMessage()
                Log.e(TAG, "Error during inference", e)
                addMessage(ChatMessage.ErrorMessage(e.message ?: "Unknown Error"))
            }
        }
    }

    /**
     * Run Koog-style agent with context injection for local model.
     */
    private fun runAgentWithContext(userQuery: String): String {
        // Build prompt with CSV context (hybrid approach)
        val prompt = buildPrompt(userQuery, currentCsvData)
        Log.v(TAG, "Prompt:\n$prompt")

        // Execute using LlamaBridge
        return LlamaBridge.generate(prompt)
    }

    /**
     * Build a prompt for the local TableLLM model with CSV context.
     */
    private fun buildPrompt(userQuery: String, csvData: String?): String {
        val tableData = csvData ?: "No CSV data loaded."
        
        // Use a simpler, more direct prompt that discourages code generation
        return """[INST]Answer the following question about this table data. Give a direct text answer, do not write any code or Python.

Table:
$tableData

Question: $userQuery

Answer directly in plain text:[INST/]"""
    }

    /**
     * Helper to add a message to history
     */
    private fun addMessage(message: ChatMessage) {
        val currentList = _chatHistory.value.toMutableList()
        currentList.add(message)
        _chatHistory.value = currentList
    }

    /**
     * Helper to remove the last ThinkingMessage
     */
    private fun removeThinkingMessage() {
        val currentList = _chatHistory.value.toMutableList()
        currentList.removeAll { it is ChatMessage.ThinkingMessage }
        _chatHistory.value = currentList
    }

    /**
     * Force reset the model state (useful for debugging).
     */
    fun resetModel() {
        Log.d(TAG, "Resetting model state")
        _isModelLoaded.value = false
        _isCsvLoaded.value = false
        currentModelPath = null
        _uiState.value = InferenceState.Idle
        _chatHistory.value = emptyList()
    }
}