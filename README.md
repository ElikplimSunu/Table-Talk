# TableTalk 📊💬

An Android app that enables local, on-device AI-powered analysis of tabular CSV data using Llama models via [Llamatik](https://llamatik.com/).

## Features

- **100% On-Device AI** - No cloud APIs, complete privacy
- **CSV Analysis** - Load any CSV and ask questions in natural language
- **Koog Agent Framework** - Built with JetBrains' [Koog](https://github.com/JetBrains/koog) for structured agent architecture
- **GGUF Model Support** - Load any compatible GGUF model from device storage

## Screenshots

*Coming soon*

## Requirements

- Android 10+ (API 29+)
- ~4GB+ free RAM (for 7B models)
- GGUF model file (e.g., TableLLM-7b-Q3_K_M.gguf)

## Quick Start

1. **Install the app** on your Android device
2. **Download a GGUF model** (or use the in-app download button)
3. **Grant storage permission** when prompted (required to read model files)
4. **Select your model** using the file picker
5. **Upload a CSV file**
6. **Ask questions** about your data!

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     MainActivity                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │              MainScreen (Compose UI)             │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                  InferenceViewModel                      │
│  • Model loading (LlamaBridge)                          │
│  • CSV parsing                                          │
│  • Koog ToolRegistry                                    │
│  • Question/Answer flow                                 │
└─────────────────────────────────────────────────────────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
┌─────────────────────┐    ┌─────────────────────────────┐
│    LlamaBridge      │    │     Koog Agent Tools        │
│  (Native llama.cpp) │    │  • AnalyzeCsvTool           │
│                     │    │  • GetCsvSchemaTool         │
│                     │    │  • SearchCsvTool            │
└─────────────────────┘    └─────────────────────────────┘
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose |
| AI Runtime | [Llamatik](https://llamatik.com/) (llama.cpp wrapper) |
| Agent Framework | [Koog](https://github.com/JetBrains/koog) by JetBrains |
| Architecture | MVVM with StateFlow |

## Project Structure

```
app/src/main/java/com/sunueric/tabletalk/
├── MainActivity.kt              # Entry point, file pickers, permissions
├── agent/
│   ├── LocalLlamaModel.kt       # Custom LLModel for Llamatik
│   ├── LocalLlamaExecutor.kt    # PromptExecutor implementation
│   └── CsvToolSet.kt            # Koog SimpleTool implementations
├── viewmodels/
│   └── InferenceViewModel.kt    # Main business logic
├── ui/
│   ├── composables/
│   │   └── MainScreen.kt        # Main UI components
│   └── states/
│       └── InferenceState.kt    # UI state sealed class
└── utils/
    ├── FileUtils.kt             # URI to path resolution
    └── ModelDownloader.kt       # Model download helper
```

## Key Implementation Details

### Hybrid Agent Approach

This app uses a **hybrid approach** for AI agent implementation:

1. **Koog ToolRegistry** - Provides structured tool definitions for potential future tool-calling models
2. **Context Injection** - CSV data is injected directly into prompts (more reliable with local models)

This works around the limitation that most local Llama models don't support proper function/tool calling.

### Storage Permissions

On Android 11+, the app requests `MANAGE_EXTERNAL_STORAGE` permission to read model files from any location without copying them (which would double storage usage for large models).

## Building

```bash
# Clone the repository
git clone https://github.com/yourusername/tabletalk.git
cd tabletalk

# Build debug APK
./gradlew :app:assembleDebug

# Install on connected device
./gradlew :app:installDebug
```

## Recommended Models

| Model | Size | RAM Needed |
|-------|------|------------|
| TableLLM-7b-Q3_K_M | 3.3 GB | ~4-5 GB |
| Llama-3.2-3B-Q4_K_M | 1.9 GB | ~3 GB |
| Phi-2-Q4_0 | 1.3 GB | ~2 GB |

## Troubleshooting

### Model fails to load

- Check storage permissions are granted
- Ensure enough free RAM
- Verify GGUF file is not corrupted

### Empty responses

- Check Logcat with tag `InferenceViewModel`
- Ensure CSV is loaded before asking questions

### Permission denied errors

- Go to Settings → Apps → TableTalk → Permissions → Files → Allow all files

## License

MIT License - See [LICENSE](LICENSE) for details.

## Credits

- [Llamatik](https://llamatik.com/) - Android LLM runtime
- [Koog](https://github.com/JetBrains/koog) - AI Agent framework by JetBrains
- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Underlying inference engine
