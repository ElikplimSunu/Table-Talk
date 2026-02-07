package com.sunueric.tabletalk.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.koog.agents.core.tools.ToolRegistry
import com.llamatik.library.platform.LlamaBridge
import com.sunueric.tabletalk.agent.AnalyzeCsvTool
import com.sunueric.tabletalk.agent.GetCsvSchemaTool
import com.sunueric.tabletalk.agent.LocalLlamaModel
import com.sunueric.tabletalk.agent.SearchCsvTool
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

    private val _uiState = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val uiState = _uiState.asStateFlow()

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
                Log.d(TAG, "File exists: ${modelFile.exists()}")
                Log.d(TAG, "File canRead: ${modelFile.canRead()}")
                Log.d(TAG, "File length: ${modelFile.length()} bytes")
                Log.d(TAG, "File absolutePath: ${modelFile.absolutePath}")
                
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
                } else {
                    Log.e(TAG, "initGenerateModel returned false - model failed to load")
                    Log.e(TAG, "Possible causes: unsupported GGUF format, corrupted file, or insufficient memory")
                    _isModelLoaded.value = false
                    currentModelPath = null
                    _uiState.value = InferenceState.Error(
                        "Model failed to load. Possible causes:\n" +
                        "• Unsupported GGUF format\n" +
                        "• Corrupted model file\n" +
                        "• Insufficient device memory for ${modelFile.length() / 1024 / 1024} MB model"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model initialization failed", e)
                _isModelLoaded.value = false
                currentModelPath = null
                _uiState.value = InferenceState.Error("Init Failed: ${e.message}")
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
            
            try {
                val uri = Uri.parse(uriString)
                val context = getApplication<Application>()
                
                Log.d(TAG, "Opening input stream for URI...")
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Could not open file")

                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines()
                reader.close()

                Log.d(TAG, "Read ${lines.size} lines from CSV")

                if (lines.isEmpty()) {
                    Log.w(TAG, "CSV file is empty")
                    _uiState.value = InferenceState.Error("Error: File is empty.")
                    return@launch
                }

                // Store CSV data
                currentCsvHeaders = lines[0]
                currentCsvLines = lines.drop(1)
                
                Log.d(TAG, "CSV Headers: $currentCsvHeaders")
                Log.d(TAG, "CSV Data rows: ${currentCsvLines.size}")

                val preview = currentCsvLines.take(10).joinToString("\n")
                currentCsvData = """
                    CSV SCHEMA & DATA:
                    Headers: $currentCsvHeaders
                    
                    Data (first 10 rows):
                    $preview
                    
                    Total rows: ${currentCsvLines.size}
                """.trimIndent()

                // Build Koog ToolRegistry with CSV context
                buildToolRegistry()
                _isCsvLoaded.value = true
                Log.i(TAG, "CSV loaded and ToolRegistry built")

                _uiState.value = InferenceState.Success(
                    "CSV Loaded!\n\n$currentCsvData\n\nAsk a question about your data!"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading CSV", e)
                _uiState.value = InferenceState.Error("Error loading CSV: ${e.message}")
            }
        }
    }

    /**
     * Build Koog ToolRegistry with CSV analysis tools.
     */
    private fun buildToolRegistry() {
        Log.d(TAG, "Building ToolRegistry...")
        toolRegistry = ToolRegistry {
            tool(AnalyzeCsvTool(currentCsvData ?: ""))
            tool(GetCsvSchemaTool(currentCsvHeaders ?: ""))
            tool(SearchCsvTool(currentCsvLines))
        }
        val toolNames = toolRegistry?.tools?.joinToString(", ") { it.name } ?: "none"
        Log.d(TAG, "ToolRegistry built with tools: $toolNames")
    }

    /**
     * Ask a question using Koog AIAgent (hybrid mode with context injection).
     */
    fun askQuestion(userQuery: String) {
        Log.d(TAG, "askQuestion called: $userQuery")
        Log.d(TAG, "Model state - isLoaded: ${_isModelLoaded.value}")

        if (!_isModelLoaded.value) {
            Log.w(TAG, "Model not loaded, rejecting question")
            _uiState.value = InferenceState.Error("Model not loaded yet. Please load a model first.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = InferenceState.Thinking("Analyzing data with AI Agent...")

            try {
                val response = runAgentWithContext(userQuery)
                
                // Handle null/empty response
                if (response.isNullOrBlank()) {
                    Log.w(TAG, "Model returned null or blank response")
                    _uiState.value = InferenceState.Error("Model returned empty response. Try reloading the model.")
                } else {
                    Log.d(TAG, "Got response (${response.length} chars)")
                    _uiState.value = InferenceState.Success(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during inference", e)
                _uiState.value = InferenceState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    /**
     * Run Koog-style agent with context injection for local model.
     */
    private suspend fun runAgentWithContext(userQuery: String): String? {
        // Log available tools (learning/demo purpose)
        val availableTools = toolRegistry?.tools?.joinToString(", ") { it.name } ?: "none"
        Log.d(TAG, "Koog Agent - Available tools: $availableTools")
        Log.d(TAG, "Koog Agent - Model: ${LocalLlamaModel.id} (${LocalLlamaModel.provider.id})")

        // Build prompt with CSV context (hybrid approach)
        val prompt = buildPrompt(userQuery, currentCsvData)
        Log.d(TAG, "Built prompt (${prompt.length} chars)")
        Log.v(TAG, "Prompt:\n$prompt")

        // Execute using LlamaBridge
        Log.d(TAG, "Calling LlamaBridge.generate...")
        val result = LlamaBridge.generate(prompt)
        Log.d(TAG, "LlamaBridge.generate returned: ${result?.take(100) ?: "null"}...")
        
        return result
    }

    /**
     * Build a prompt for the local Llama model with CSV context.
     */
    private fun buildPrompt(userQuery: String, csvData: String?): String {
        val dataContext = csvData ?: "No CSV data loaded."
        
        return """
            <|start_header_id|>system<|end_header_id|>

            You are a Data Analyst assistant. Analyze the provided CSV data and answer the user's question.
            Be precise with numbers and always show your reasoning for calculations.
            <|eot_id|><|start_header_id|>user<|end_header_id|>

            Here is the dataset:
            $dataContext

            Question: $userQuery
            <|eot_id|><|start_header_id|>assistant<|end_header_id|>

        """.trimIndent()
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
    }
}