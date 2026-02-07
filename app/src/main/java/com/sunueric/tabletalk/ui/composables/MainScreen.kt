package com.sunueric.tabletalk.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunueric.tabletalk.ui.states.InferenceState
import com.sunueric.tabletalk.utils.ModelDownloader

@Composable
fun MainScreen(
    uiState: InferenceState,
    onPickModel: () -> Unit,
    onPickCsv: () -> Unit = {},
    onAskQuestion: (String) -> Unit = {},
    isModelLoaded: Boolean = false,
    isCsvLoaded: Boolean = false
) {
    val context = LocalContext.current
    var userQuestion by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Tabular AI Explorer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // State display card
            when (uiState) {
                is InferenceState.Idle -> IdleView()
                is InferenceState.Thinking -> ThinkingView(message = uiState.currentStep)
                is InferenceState.Success -> SuccessView(answer = uiState.answer)
                is InferenceState.Error -> ErrorView(message = uiState.message)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Chat input section - always show when model is loaded
            if (isModelLoaded) {
                ChatInputSection(
                    userQuestion = userQuestion,
                    onQuestionChange = { userQuestion = it },
                    onSend = {
                        if (userQuestion.isNotBlank()) {
                            onAskQuestion(userQuestion)
                            userQuestion = ""
                        }
                    },
                    onPickCsv = onPickCsv,
                    isCsvLoaded = isCsvLoaded
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Model selection buttons
            Button(
                onClick = onPickModel,
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Model File (.gguf)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { ModelDownloader.downloadModel(context) },
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Model (WiFi Only)")
            }
        }
    }
}

@Composable
fun ChatInputSection(
    userQuestion: String,
    onQuestionChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickCsv: () -> Unit,
    isCsvLoaded: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // CSV picker button with status
            OutlinedButton(
                onClick = onPickCsv,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isCsvLoaded) "✓ CSV Loaded - Upload Another" else "Upload CSV File")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Question input with send button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userQuestion,
                    onValueChange = onQuestionChange,
                    placeholder = { Text("Ask a question about your data...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSend,
                    enabled = userQuestion.isNotBlank()
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (userQuestion.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

@Composable
fun IdleView() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Ready to Start", style = MaterialTheme.typography.titleMedium)
            Text("Select a model file to begin.")
        }
    }
}

@Composable
fun ThinkingView(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun SuccessView(answer: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Agent Response:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ErrorView(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}