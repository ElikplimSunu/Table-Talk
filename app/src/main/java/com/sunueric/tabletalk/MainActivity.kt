package com.sunueric.tabletalk

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sunueric.tabletalk.ui.composables.MainScreen
import com.sunueric.tabletalk.ui.theme.TableTalkTheme
import com.sunueric.tabletalk.utils.FileUtils
import com.sunueric.tabletalk.viewmodels.InferenceViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: InferenceViewModel by viewModels()

    private var isModelLoaded by mutableStateOf(false)

    private lateinit var modelPickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var csvPickerLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Launcher for model file picker (.gguf)
        modelPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                val path = FileUtils.getPathFromUri(this, it)
                if (path != null) {
                    viewModel.initializeAndTest(path)
                    isModelLoaded = true
                } else {
                    Toast.makeText(this, "Failed to resolve model path", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Launcher for CSV file picker
        csvPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                // Keep read permission for this URI
                try {
                    contentResolver.takePersistableUriPermission(
                        it,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Permission might not be persistable, that's ok
                }
                viewModel.loadCsvFile(it.toString())
            }
        }

        setContent {
            TableTalkTheme {
                MainScreen(
                    uiState = viewModel.uiState.collectAsState().value,
                    onPickModel = { modelPickerLauncher.launch(arrayOf("*/*")) },
                    onPickCsv = { csvPickerLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "*/*")) },
                    onAskQuestion = { question -> viewModel.askQuestion(question) },
                    isModelLoaded = isModelLoaded
                )
            }
        }
    }
}