package com.sunueric.tabletalk.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunueric.tabletalk.data.ChatMessage
import com.sunueric.tabletalk.ui.states.InferenceState
import com.sunueric.tabletalk.utils.ModelDownloader
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: InferenceState,
    chatHistoryState: StateFlow<List<ChatMessage>>,
    onPickModel: () -> Unit,
    onPickCsv: () -> Unit = {},
    onAskQuestion: (String) -> Unit = {},
    isModelLoaded: Boolean = false,
    isCsvLoaded: Boolean = false,
    onReset: () -> Unit = {}
) {
    val context = LocalContext.current
    var userQuestion by remember { mutableStateOf("") }
    val chatHistory by chatHistoryState.collectAsState(initial = emptyList())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Table Talk",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary, // Navy
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (isModelLoaded) {
                        IconButton(onClick = onReset) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background) // Uses the new clean background
                .imePadding() // Adjust for keyboard
        ) {
            
            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (!isModelLoaded) {
                    // Show Setup Screen when no model is loaded
                    ModelSetupScreen(
                        uiState = uiState,
                        onPickModel = onPickModel,
                        onDownloadModel = { ModelDownloader.downloadModel(context) }
                    )
                } else {
                    // Show Chat Interface
                    if (chatHistory.isEmpty()) {
                        EmptyChatState()
                    } else {
                        MessageList(messages = chatHistory)
                    }
                }
            }

            // Input Section (Only visible when model is loaded)
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
            }
        }
    }
}

@Composable
fun ModelSetupScreen(
    uiState: InferenceState,
    onPickModel: () -> Unit,
    onDownloadModel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.sunueric.tabletalk.R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary // Use brand color
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Welcome to Table Talk",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "To get started, please load a local Llama model (.gguf).",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onPickModel,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Model File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onDownloadModel,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download Model (WiFi Only)")
        }
        
        // Show status/error messages here during setup
        if (uiState is InferenceState.Error) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        if (uiState is InferenceState.Thinking) {
             Spacer(modifier = Modifier.height(24.dp))
             CircularProgressIndicator()
             Spacer(modifier = Modifier.height(8.dp))
             Text(uiState.currentStep)
        }
    }
}

@Composable
fun EmptyChatState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No messages yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Upload a CSV to start analyzing data",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // CSV Status / Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCsvLoaded) {
                     Text(
                        text = "✓ CSV Active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = onPickCsv,
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Change", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                     OutlinedButton(
                        onClick = onPickCsv,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload CSV File")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input Field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userQuestion,
                    onValueChange = onQuestionChange,
                    placeholder = { Text("Ask about your data...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSend,
                    enabled = userQuestion.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (userQuestion.isNotBlank()) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (userQuestion.isNotBlank())
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}