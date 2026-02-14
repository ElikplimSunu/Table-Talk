package com.sunueric.tabletalk

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sunueric.tabletalk.ui.composables.MainScreen
import com.sunueric.tabletalk.ui.theme.TableTalkTheme
import com.sunueric.tabletalk.utils.FileUtils
import com.sunueric.tabletalk.viewmodels.InferenceViewModel
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: InferenceViewModel by viewModels()

    private var pendingModelUri: Uri? = null

    private lateinit var modelPickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var csvPickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Storage permission launcher for Android 11+
        storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // Check if permission was granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                Environment.isExternalStorageManager()) {
                Log.d(TAG, "Storage permission granted")
                // Retry loading the pending model
                pendingModelUri?.let { loadModelFromUri(it) }
            } else {
                Log.w(TAG, "Storage permission denied")
                Toast.makeText(this, "Storage permission required to read model files", Toast.LENGTH_LONG).show()
            }
        }

        // Launcher for model file picker (.gguf)
        modelPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let { loadModelFromUri(it) }
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
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Permission might not be persistable, that's ok
                }
                viewModel.loadCsvFile(it.toString())
            }
        }

        setContent {
            TableTalkTheme {
                // Collect states from ViewModel
                val uiState by viewModel.uiState.collectAsState()
                val isModelLoaded by viewModel.isModelLoaded.collectAsState()
                val isCsvLoaded by viewModel.isCsvLoaded.collectAsState()
                
                MainScreen(
                    uiState = uiState,
                    chatHistoryState = viewModel.chatHistory,
                    onPickModel = { modelPickerLauncher.launch(arrayOf("*/*")) },
                    onPickCsv = { csvPickerLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "*/*")) },
                    onAskQuestion = { question -> viewModel.askQuestion(question) },
                    isModelLoaded = isModelLoaded,
                    isCsvLoaded = isCsvLoaded,
                    onReset = { viewModel.resetModel() }
                )
            }
        }
    }
    
    private fun loadModelFromUri(uri: Uri) {
        Log.d(TAG, "loadModelFromUri: $uri")
        
        // Check if we have storage permission on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
            !Environment.isExternalStorageManager()) {
            Log.d(TAG, "Requesting MANAGE_EXTERNAL_STORAGE permission")
            pendingModelUri = uri
            
            Toast.makeText(
                this, 
                "Please grant 'All files access' permission to load model files", 
                Toast.LENGTH_LONG
            ).show()
            
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:$packageName".toUri()
            }
            storagePermissionLauncher.launch(intent)
            return
        }
        
        // Try to get the file path
        val path = FileUtils.getPathFromUri(this, uri)
        if (path != null) {
            Log.d(TAG, "Got path: $path")
            viewModel.initializeAndTest(path)
        } else {
            Log.e(TAG, "Failed to resolve model path")
            Toast.makeText(this, "Failed to access model file. Check storage permissions.", Toast.LENGTH_LONG).show()
        }
    }
}