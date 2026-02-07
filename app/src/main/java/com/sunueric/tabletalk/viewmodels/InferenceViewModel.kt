package com.sunueric.tabletalk.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.llamatik.library.platform.LlamaBridge
import com.sunueric.tabletalk.ui.states.InferenceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ViewModel for managing AI inference with local Llama model.
 * Uses AndroidViewModel to access Application context for CSV file reading.
 */
class InferenceViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val uiState = _uiState.asStateFlow()

    private var isModelLoaded = false
    private var currentCsvData: String? = null
    private var currentCsvUri: String? = null

    /**
     * Initialize the local Llama model.
     */
    fun initializeAndTest(modelPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = InferenceState.Thinking("Loading AI Model...")

            try {
                LlamaBridge.initGenerateModel(modelPath)
                isModelLoaded = true
                _uiState.value = InferenceState.Success("Model Ready! Upload a CSV file to start analyzing.")
            } catch (e: Exception) {
                _uiState.value = InferenceState.Error("Init Failed: ${e.message}")
            }
        }
    }

    /**
     * Load and preview a CSV file.
     */
    fun loadCsvFile(uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = InferenceState.Thinking("Loading CSV file...")
            
            try {
                val uri = Uri.parse(uriString)
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Could not open file")

                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines()
                reader.close()

                if (lines.isEmpty()) {
                    _uiState.value = InferenceState.Error("Error: File is empty.")
                    return@launch
                }

                val headers = lines[0]
                val preview = lines.drop(1).take(10).joinToString("\n")
                
                currentCsvUri = uriString
                currentCsvData = """
                    CSV SCHEMA & DATA:
                    Headers: $headers
                    
                    Data (first 10 rows):
                    $preview
                    
                    Total rows: ${lines.size - 1}
                """.trimIndent()

                _uiState.value = InferenceState.Success(
                    "CSV Loaded!\n\n$currentCsvData\n\nAsk a question about your data!"
                )
            } catch (e: Exception) {
                _uiState.value = InferenceState.Error("Error loading CSV: ${e.message}")
            }
        }
    }

    /**
     * Ask a question about the loaded CSV data.
     */
    fun askQuestion(userQuery: String) {
        if (!isModelLoaded) {
            _uiState.value = InferenceState.Error("Model not loaded yet. Please load a model first.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = InferenceState.Thinking("Analyzing data...")

            try {
                // Build the prompt with CSV context
                val prompt = buildPrompt(userQuery, currentCsvData)
                
                // Generate response using local model
                val response = LlamaBridge.generate(prompt)

                _uiState.value = InferenceState.Success(response)
            } catch (e: Exception) {
                _uiState.value = InferenceState.Error(e.message ?: "Unknown Error")
            }
        }
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
}